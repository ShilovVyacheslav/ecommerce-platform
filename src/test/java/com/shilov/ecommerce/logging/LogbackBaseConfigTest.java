package com.shilov.ecommerce.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static net.logstash.logback.argument.StructuredArguments.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("logback-base.xml")
public class LogbackBaseConfigTest {

    private static final String SERVICE_NAME = "logging-template-test";
    private static final String JWT = String.join(".",
            base64Url("{\"alg\":\"RS256\"}"), base64Url("{\"sub\":\"admin\",\"id\":1}"), base64Url("signature-signature"));

    private static final Logger log = LoggerFactory.getLogger(LogbackBaseConfigTest.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConfigurableApplicationContext context;

    @AfterEach
    void closeContext() {
        MDC.clear();
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("JSON format -> one JSON object per line with service metadata, MDC and structured fields")
    void json_LogEvent_WritesStructuredLine(CapturedOutput output) throws Exception {
        start("json");
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        log.atInfo().addKeyValue("amount", 346.47).log("Order {} confirmed", v("orderId", "order-1"));

        JsonNode line = awaitJsonLine(output, "Order order-1 confirmed");
        assertThat(line.path("@timestamp").asText()).endsWith("Z");
        assertThat(line.path("level").asText()).isEqualTo("INFO");
        assertThat(line.path("service").asText()).isEqualTo(SERVICE_NAME);
        assertThat(line.path("env").asText()).isEqualTo("test");
        assertThat(line.path("logger").asText()).endsWith("LogbackBaseConfigTest");
        assertThat(line.path("correlationId").asText()).isEqualTo(correlationId);
        assertThat(line.path("orderId").asText()).isEqualTo("order-1");
        assertThat(line.path("amount").asDouble()).isEqualTo(346.47);
    }

    @Test
    @DisplayName("Exception -> error type, root cause, fingerprint and a bounded stack trace")
    void json_LogEventWithException_WritesErrorFields(CapturedOutput output) throws Exception {
        start("json");

        log.error("Payment call failed", new IllegalStateException("gateway down",
                new SocketTimeoutException("Read timed out")));

        JsonNode line = awaitJsonLine(output, "Payment call failed");
        assertThat(line.path("errorType").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(line.path("errorRootCause").asText()).isEqualTo("java.net.SocketTimeoutException");
        assertThat(line.path("errorHash").asText()).isNotBlank();
        assertThat(line.path("stackTrace").asText())
                .startsWith("java.net.SocketTimeoutException")
                .hasSizeLessThanOrEqualTo(8192);
    }

    @Test
    @DisplayName("Secrets -> masked both as structured fields and inside the message text")
    void json_Secrets_AreMasked(CapturedOutput output) throws Exception {
        start("json");

        log.atInfo()
                .addKeyValue("password", "s3cr3t-password")
                .addKeyValue("apiKey", "internal-api-key")
                .log("Calling with Authorization: Bearer {} and token {}", "abcdefghijklmnop", JWT);

        JsonNode line = awaitJsonLine(output, "Calling with Authorization");
        assertThat(line.path("message").asText())
                .doesNotContain("abcdefghijklmnop")
                .doesNotContain(JWT)
                .contains("****");
        assertThat(line.path("password").asText()).isEqualTo("****");
        assertThat(line.path("apiKey").asText()).isEqualTo("****");
        assertThat(output.getOut()).doesNotContain("s3cr3t-password", "internal-api-key");
    }

    @Test
    @DisplayName("Huge message -> cut to the configured maximum length")
    void json_HugeMessage_IsTruncated(CapturedOutput output) throws Exception {
        start("json");

        log.warn("Huge payload: {}", "x".repeat(20_000));

        JsonNode line = awaitJsonLine(output, "Huge payload");
        assertThat(line.path("message").asText()).hasSize(4096);
    }

    @Test
    @DisplayName("Text format -> human-readable line with the correlation id, no JSON")
    void text_LogEvent_WritesReadableLine(CapturedOutput output) {
        start("text");
        MDC.put(CORRELATION_ID_MDC_KEY, "corr-123");

        log.info("Readable line");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut())
                .containsPattern("INFO .*\\[corr-123] .*LogbackBaseConfigTest\\s+: Readable line"));
        assertThat(output.getOut().lines().filter(l -> l.contains("Readable line")))
                .noneMatch(l -> l.startsWith("{"));
    }

    private void start(String format) {
        long startedAt = System.currentTimeMillis();
        context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.application.name=" + SERVICE_NAME,
                        "app.environment=test",
                        "app.logging.format=" + format)
                .run();
        assertNoLogbackConfigurationWarnings(startedAt);
    }

    private static void assertNoLogbackConfigurationWarnings(long since) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertThat(loggerContext.getStatusManager().getCopyOfStatusList())
                .filteredOn(status -> status.getTimestamp() >= since && status.getLevel() >= Status.WARN)
                .extracting(Status::getMessage)
                .isEmpty();
    }

    private JsonNode awaitJsonLine(CapturedOutput output, String messageFragment) throws Exception {
        await().atMost(Duration.ofSeconds(5)).until(() -> output.getOut().contains(messageFragment));
        String line = output.getOut().lines()
                .filter(candidate -> candidate.startsWith("{") && candidate.contains(messageFragment))
                .findFirst()
                .orElseThrow();
        return objectMapper.readTree(line);
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }

}
