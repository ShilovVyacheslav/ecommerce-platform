package com.shilov.ecommerce.apigateway.logging;

import com.shilov.ecommerce.logging.LogActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static net.logstash.logback.argument.StructuredArguments.v;

@Component
public class AccessLogWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("com.shilov.ecommerce.access");

    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of(
            "/actuator", "/swagger-ui", "/webjars", "/favicon.ico");
    private static final String API_DOCS_PATH = "/v3/api-docs";
    private static final int MAX_PATH_LENGTH = 256;

    private final Duration slowRequestThreshold;

    public AccessLogWebFilter(@Value("${app.logging.access.slow-request-threshold:2s}") Duration slowRequestThreshold) {
        this.slowRequestThreshold = slowRequestThreshold;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith) || path.contains(API_DOCS_PATH)) {
            return chain.filter(exchange);
        }
        long startNanos = System.nanoTime();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        return chain.filter(exchange)
                .doOnError(failure::set)
                .doFinally(signal -> logExchange(exchange, signal, failure.get(), startNanos));
    }

    private void logExchange(ServerWebExchange exchange, SignalType signal, Throwable failure, long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        int status = resolveStatus(exchange, failure);
        boolean slow = durationMs >= slowRequestThreshold.toMillis();
        Level level = status >= 500 || slow ? Level.WARN : Level.INFO;
        if (!log.isEnabledForLevel(level)) {
            return;
        }

        ServerHttpRequest request = exchange.getRequest();
        LoggingEventBuilder event = log.atLevel(level);
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            event.addKeyValue("routeId", route.getId());
            event.addKeyValue("action", LogActions.gateway(route.getId()));
        }
        String clientIp = clientIp(request);
        if (clientIp != null) {
            event.addKeyValue("clientIp", clientIp);
        }

        String suffix = signal == SignalType.CANCEL ? " (cancelled by the client)" : slow ? " (slow request)" : "";
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE);
        String previousCorrelationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        }
        try {
            event.log("{} {} -> {} in {} ms{}",
                    v("httpMethod", request.getMethod().name()),
                    v("httpPath", truncate(request.getPath().value())),
                    v("httpStatus", status),
                    v("durationMs", durationMs),
                    suffix);
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CORRELATION_ID_MDC_KEY);
            } else {
                MDC.put(CORRELATION_ID_MDC_KEY, previousCorrelationId);
            }
        }
    }

    private static int resolveStatus(ServerWebExchange exchange, Throwable failure) {
        if (failure instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode().value();
        }
        if (failure != null) {
            return 500;
        }
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        return statusCode != null ? statusCode.value() : 200;
    }

    private static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : null;
    }

    private static String truncate(String path) {
        return path.length() <= MAX_PATH_LENGTH ? path : path.substring(0, MAX_PATH_LENGTH);
    }

}
