package com.shilov.ecommerce.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shilov.ecommerce.enums.ErrorType;
import com.shilov.ecommerce.enums.ServiceName;
import com.shilov.ecommerce.exception.ApplicationException;
import com.shilov.ecommerce.handler.GlobalExceptionHandler;
import com.shilov.ecommerce.logging.RequestContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.USER_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Request logging (CorrelationIdFilter + RequestLoggingFilter + RequestContextInterceptor)")
public class RequestLoggingFilterTest {

    private static final int NOT_FOUND_CODE = 1234;

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.ACCESS_LOGGER);
    private final ListAppender<ILoggingEvent> accessLog = new ListAppender<>();

    private static final Map<String, String> MDC_INSIDE_HANDLER = new HashMap<>();

    @BeforeEach
    void captureAccessLog() {
        accessLog.start();
        accessLogger.addAppender(accessLog);
        accessLogger.setLevel(Level.INFO);
        MDC_INSIDE_HANDLER.clear();
    }

    @AfterEach
    void releaseAccessLog() {
        accessLogger.detachAppender(accessLog);
        MDC.clear();
    }

    @Nested
    @DisplayName("Correlation id")
    class CorrelationId {

        @Test
        @DisplayName("Valid incoming id -> kept, echoed in the response and visible to the handler")
        void request_WithValidCorrelationId_PropagatesIt() throws Exception {
            mockMvc(Duration.ofSeconds(2)).perform(get("/api/v1/things/42").header(CORRELATION_ID_HEADER, "corr-42"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(CORRELATION_ID_HEADER, "corr-42"));

            assertThat(MDC_INSIDE_HANDLER).containsEntry(CORRELATION_ID_MDC_KEY, "corr-42");
            assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).as("MDC is cleaned after the request").isNull();
        }

        @Test
        @DisplayName("Unsafe incoming id -> replaced, the generated id is echoed instead")
        void request_WithInjectedCorrelationId_ReplacesIt() throws Exception {
            String echoed = mockMvc(Duration.ofSeconds(2))
                    .perform(get("/api/v1/things/42").header(CORRELATION_ID_HEADER, "x\r\nlevel=ERROR forged"))
                    .andReturn().getResponse().getHeader(CORRELATION_ID_HEADER);

            assertThat(echoed).isNotBlank().doesNotContain("forged");
            assertThat(MDC_INSIDE_HANDLER).containsEntry(CORRELATION_ID_MDC_KEY, echoed);
        }

    }

    @Nested
    @DisplayName("Access log")
    class AccessLog {

        @Test
        @DisplayName("Successful request -> exactly one INFO line with method, path, route, status, duration and action")
        void request_Succeeds_LogsOneInfoLine() throws Exception {
            mockMvc(Duration.ofSeconds(2)).perform(get("/api/v1/things/42")).andExpect(status().isOk());

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).matches("GET /api/v1/things/42 -> 200 in \\d+ ms");
                assertThat(keyValues(event))
                        .containsEntry("httpRoute", "/api/v1/things/{id}")
                        .containsEntry("action", "http:TestController.getThing")
                        .doesNotContainKeys("userId", "errorCode");
            });
        }

        @Test
        @DisplayName("Authenticated request -> numeric user id from the JWT, in the MDC and in the access log")
        void request_Authenticated_LogsUserId() throws Exception {
            mockMvc(Duration.ofSeconds(2)).perform(get("/api/v1/things/42").principal(jwtFor(7L)))
                    .andExpect(status().isOk());

            assertThat(MDC_INSIDE_HANDLER)
                    .containsEntry(USER_ID_MDC_KEY, "7")
                    .containsEntry(ACTION_MDC_KEY, "http:TestController.getThing");
            assertThat(accessLog.list).singleElement()
                    .satisfies(event -> assertThat(keyValues(event)).containsEntry("userId", "7"));
        }

        @Test
        @DisplayName("Business rejection (4xx) -> INFO with the error code, no WARN noise")
        void request_RejectedWithApplicationException_LogsInfoWithErrorCode() throws Exception {
            mockMvc(Duration.ofSeconds(2)).perform(get("/api/v1/things/42/missing")).andExpect(status().isNotFound());

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).startsWith("GET /api/v1/things/42/missing -> 404");
                assertThat(keyValues(event)).containsEntry("errorCode", String.valueOf(NOT_FOUND_CODE));
            });
        }

        @Test
        @DisplayName("Unhandled failure mapped to 500 -> WARN")
        void request_FailsWith500_LogsWarn() throws Exception {
            mockMvc(Duration.ofSeconds(2)).perform(get("/api/v1/boom")).andExpect(status().isInternalServerError());

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).startsWith("GET /api/v1/boom -> 500");
            });
        }

        @Test
        @DisplayName("Exception escapes the filter chain -> still logged, as 500, and rethrown")
        void request_ExceptionEscapes_LogsAs500() {
            MockMvc mockMvcWithoutAdvice = MockMvcBuilders.standaloneSetup(new TestController())
                    .addFilters(new CorrelationIdFilter(), new RequestLoggingFilter(Duration.ofSeconds(2)))
                    .build();

            assertThatThrownBy(() -> mockMvcWithoutAdvice.perform(get("/api/v1/boom")))
                    .hasRootCauseInstanceOf(IllegalStateException.class);

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).startsWith("GET /api/v1/boom -> 500");
            });
        }

        @Test
        @DisplayName("Request slower than the threshold -> WARN marked as slow")
        void request_Slow_LogsWarn() throws Exception {
            mockMvc(Duration.ZERO).perform(get("/api/v1/things/42")).andExpect(status().isOk());

            assertThat(accessLog.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).endsWith("(slow request)");
            });
        }

        @Test
        @DisplayName("Health probe -> not logged")
        void request_Actuator_IsNotLogged() throws Exception {
            mockMvc(Duration.ofSeconds(2)).perform(get("/actuator/health"));

            assertThat(accessLog.list).isEmpty();
        }

        @Test
        @DisplayName("Access logger raised to WARN -> successful requests are not logged at all")
        void request_AccessLoggerAtWarn_SkipsSuccessfulRequests() throws Exception {
            accessLogger.setLevel(Level.WARN);

            mockMvc(Duration.ofSeconds(2)).perform(get("/api/v1/things/42")).andExpect(status().isOk());

            assertThat(accessLog.list).isEmpty();
        }

    }

    private static MockMvc mockMvc(Duration slowRequestThreshold) {
        return MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RequestContextInterceptor())
                .addFilters(new CorrelationIdFilter(), new RequestLoggingFilter(slowRequestThreshold))
                .build();
    }

    private static JwtAuthenticationToken jwtFor(long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("john")
                .claim("id", userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs() == null ? Map.of() : event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, (KeyValuePair pair) -> String.valueOf(pair.value)));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/things/{id}")
        String getThing(@PathVariable("id") String id) {
            MDC_INSIDE_HANDLER.putAll(MDC.getCopyOfContextMap());
            return id;
        }

        @GetMapping("/api/v1/things/{id}/missing")
        String missingThing() {
            throw new ThingNotFoundException();
        }

        @GetMapping("/api/v1/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }

    }

    static class ThingNotFoundException extends ApplicationException {

        ThingNotFoundException() {
            super("Thing was not found", NOT_FOUND_CODE, ErrorType.NOT_FOUND_ERROR, ServiceName.PLATFORM_SERVICE,
                    HttpStatus.NOT_FOUND);
        }

    }

}
