package com.shilov.ecommerce.notificationservice.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("CorrelationIdRecordInterceptor")
public class CorrelationIdRecordInterceptorTest {

    private final CorrelationIdRecordInterceptor interceptor = new CorrelationIdRecordInterceptor();

    @SuppressWarnings("unchecked")
    private final Consumer<Object, Object> consumer = mock(Consumer.class);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Record with X-Correlation-Id -> the producer's id and the topic action are in the MDC while it is processed")
    void intercept_RecordWithCorrelationId_ContinuesIt() {
        ConsumerRecord<Object, Object> record = record();
        record.headers().add(CORRELATION_ID_HEADER, "corr-from-order".getBytes(StandardCharsets.UTF_8));

        assertThat(interceptor.intercept(record, consumer)).isSameAs(record);

        assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).isEqualTo("corr-from-order");
        assertThat(MDC.get(ACTION_MDC_KEY)).isEqualTo("kafka:order-events");
    }

    @Test
    @DisplayName("Record without the header (older producer) -> a new id is generated")
    void intercept_RecordWithoutCorrelationId_GeneratesOne() {
        interceptor.intercept(record(), consumer);

        assertThat(UUID.fromString(MDC.get(CORRELATION_ID_MDC_KEY))).isNotNull();
    }

    @Test
    @DisplayName("After the record -> MDC cleaned, the next record on the thread starts clean")
    void afterRecord_CleansMdc() {
        ConsumerRecord<Object, Object> record = record();
        interceptor.intercept(record, consumer);

        interceptor.afterRecord(record, consumer);

        assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(ACTION_MDC_KEY)).isNull();
    }

    private static ConsumerRecord<Object, Object> record() {
        return new ConsumerRecord<>("order-events", 0, 42L, "order-id", "{}");
    }

}
