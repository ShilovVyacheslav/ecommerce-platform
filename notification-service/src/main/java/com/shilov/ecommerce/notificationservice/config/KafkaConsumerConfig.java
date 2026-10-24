package com.shilov.ecommerce.notificationservice.config;

import com.shilov.ecommerce.logging.CorrelationIds;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static net.logstash.logback.argument.StructuredArguments.v;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, ex) -> {
            boolean restoreContext = MDC.get(CORRELATION_ID_MDC_KEY) == null;
            if (restoreContext) {
                MDC.put(CORRELATION_ID_MDC_KEY,
                        CorrelationIds.acceptOrGenerate(CorrelationIdRecordInterceptor.correlationId(record)));
            }
            try {
                log.atError()
                        .setCause(ex)
                        .addKeyValue("kafkaKey", record.key())
                        .log("Record {}-{}@{} failed after all retries, sending it to {}",
                                v("kafkaTopic", record.topic()), v("kafkaPartition", record.partition()),
                                v("kafkaOffset", record.offset()), record.topic() + DLT_SUFFIX);
            } finally {
                if (restoreContext) {
                    MDC.remove(CORRELATION_ID_MDC_KEY);
                }
            }
            deadLetterPublisher.accept(record, ex);
        }, new FixedBackOff(1000L, 3L));
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor());
        return factory;
    }

}
