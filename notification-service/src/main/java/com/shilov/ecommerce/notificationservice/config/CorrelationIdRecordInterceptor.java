package com.shilov.ecommerce.notificationservice.config;

import com.shilov.ecommerce.logging.CorrelationIds;
import com.shilov.ecommerce.logging.LogActions;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        MDC.put(CORRELATION_ID_MDC_KEY, CorrelationIds.acceptOrGenerate(correlationId(record)));
        MDC.put(ACTION_MDC_KEY, LogActions.kafka(record.topic()));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CORRELATION_ID_MDC_KEY);
        MDC.remove(ACTION_MDC_KEY);
    }

    static String correlationId(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

}
