package com.shilov.ecommerce.apigateway.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gateway logging filters")
public class GatewayLoggingFiltersTest {

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("com.shilov.ecommerce.access");
    private final ListAppender<ILoggingEvent> accessLog = new ListAppender<>();

    @BeforeEach
    void captureAccessLog() {
        accessLog.start();
        accessLogger.addAppender(accessLog);
        accessLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void releaseAccessLog() {
        accessLogger.detachAppender(accessLog);
    }

    @Nested
    @DisplayName("CorrelationIdWebFilter")
    class CorrelationIdFilter {

        private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

        @Test
        @DisplayName("Valid incoming id -> forwarded downstream unchanged and echoed to the client")
        void filter_ValidCorrelationId_ForwardsAndEchoesIt() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/orders")
                    .header(CORRELATION_ID_HEADER, "client-corr-1"));
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

            filter.filter(exchange, completingChain(forwarded, HttpStatus.OK)).block();

            assertThat(forwarded.get().getRequest().getHeaders().get(CORRELATION_ID_HEADER))
                    .containsExactly("client-corr-1");
            assertThat(exchange.getResponse().getHeaders().get(CORRELATION_ID_HEADER))
                    .containsExactly("client-corr-1");
        }

        @Test
        @DisplayName("Missing or unsafe id -> one generated id is forwarded and echoed")
        void filter_InvalidCorrelationId_GeneratesOne() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/orders")
                    .header(CORRELATION_ID_HEADER, "bad id\r\nforged"));
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

            filter.filter(exchange, completingChain(forwarded, HttpStatus.OK)).block();

            String forwardedId = forwarded.get().getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
            assertThat(forwardedId).isNotBlank().doesNotContain("forged");
            assertThat(exchange.getResponse().getHeaders().get(CORRELATION_ID_HEADER)).containsExactly(forwardedId);
        }

        @Test
        @DisplayName("Downstream echoes its own header -> the client still gets exactly one value")
        void filter_DownstreamEchoesHeader_ClientGetsSingleValue() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/orders")
                    .header(CORRELATION_ID_HEADER, "client-corr-2"));

            filter.filter(exchange, forwardedExchange -> {
                forwardedExchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, "client-corr-2");
                return forwardedExchange.getResponse().setComplete();
            }).block();

            assertThat(exchange.getResponse().getHeaders().get(CORRELATION_ID_HEADER))
                    .containsExactly("client-corr-2");
        }

    }

    @Nested
    @DisplayName("AccessLogWebFilter")
    class AccessLogFilter {

        @Test
        @DisplayName("Routed request -> one INFO line with status, duration, route, action, client IP and correlation id")
        void filter_RoutedRequest_LogsOneInfoLine() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/orders")
                    .remoteAddress(new InetSocketAddress("203.0.113.7", 51000)));
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route("order-service"));
            exchange.getAttributes().put(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE, "corr-7");

            new AccessLogWebFilter(Duration.ofSeconds(2))
                    .filter(exchange, completingChain(new AtomicReference<>(), HttpStatus.CREATED)).block();

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).matches("POST /api/v1/orders -> 201 in \\d+ ms");
                assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", "corr-7");
                assertThat(keyValues(event))
                        .containsEntry("routeId", "order-service")
                        .containsEntry("action", "gateway:order-service")
                        .containsEntry("clientIp", "203.0.113.7");
            });
        }

        @Test
        @DisplayName("Downstream failure (5xx) -> WARN")
        void filter_DownstreamFails_LogsWarn() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/products"));

            new AccessLogWebFilter(Duration.ofSeconds(2))
                    .filter(exchange, completingChain(new AtomicReference<>(), HttpStatus.SERVICE_UNAVAILABLE)).block();

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).startsWith("GET /api/v1/products -> 503");
            });
        }

        @Test
        @DisplayName("Rate-limited request (429) -> INFO, it is an expected outcome")
        void filter_RateLimited_LogsInfo() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/auth/login"));

            new AccessLogWebFilter(Duration.ofSeconds(2))
                    .filter(exchange, completingChain(new AtomicReference<>(), HttpStatus.TOO_MANY_REQUESTS)).block();

            assertThat(accessLog.list).singleElement()
                    .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
        }

        @Test
        @DisplayName("Chain fails with a status error (e.g. no route) -> logged with that status, before the error handler runs")
        void filter_ChainFailsWithStatusError_LogsItsStatus() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/unknown"));

            new AccessLogWebFilter(Duration.ofSeconds(2))
                    .filter(exchange, failingChain(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                    .onErrorComplete()
                    .block();

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).startsWith("GET /api/v1/unknown -> 404");
            });
        }

        @Test
        @DisplayName("Chain fails with an unexpected error -> logged as 500 WARN, not as the default 200")
        void filter_ChainFailsUnexpectedly_LogsAs500() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/orders"));

            new AccessLogWebFilter(Duration.ofSeconds(2))
                    .filter(exchange, failingChain(new IllegalStateException("connection reset")))
                    .onErrorComplete()
                    .block();

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).startsWith("GET /api/v1/orders -> 500");
            });
        }

        @Test
        @DisplayName("Health probe and API docs -> not logged")
        void filter_ProbesAndDocs_AreNotLogged() {
            AccessLogWebFilter filter = new AccessLogWebFilter(Duration.ofSeconds(2));

            filter.filter(exchange(MockServerHttpRequest.get("/actuator/health")),
                    completingChain(new AtomicReference<>(), HttpStatus.OK)).block();
            filter.filter(exchange(MockServerHttpRequest.get("/order-service/v3/api-docs")),
                    completingChain(new AtomicReference<>(), HttpStatus.OK)).block();

            assertThat(accessLog.list).isEmpty();
        }

    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private static WebFilterChain completingChain(AtomicReference<ServerWebExchange> forwarded, HttpStatus status) {
        return forwardedExchange -> {
            forwarded.set(forwardedExchange);
            forwardedExchange.getResponse().setStatusCode(status);
            return forwardedExchange.getResponse().setComplete();
        };
    }

    private static WebFilterChain failingChain(Throwable failure) {
        return forwardedExchange -> Mono.error(failure);
    }

    private static Route route(String id) {
        return Route.async()
                .id(id)
                .uri(URI.create("lb://" + id))
                .predicate(exchange -> true)
                .build();
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs() == null ? Map.of() : event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

}
