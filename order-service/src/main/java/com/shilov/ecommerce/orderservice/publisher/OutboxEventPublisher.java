package com.shilov.ecommerce.orderservice.publisher;

import com.shilov.ecommerce.logging.LogActions;
import com.shilov.ecommerce.orderservice.entity.OutboxEvent;
import com.shilov.ecommerce.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisher {

    private static final String TOPIC = "order-events";
    private static final int BATCH_SIZE = 20;
    private static final String ACTION = LogActions.job(OutboxEventPublisher.class, "publishPendingEvents");

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedRate = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublishedBatch(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        MDC.put(ACTION_MDC_KEY, ACTION);
        int failed = 0;
        try {
            for (OutboxEvent outboxEvent : batch) {
                if (!publish(outboxEvent, failed == 0)) {
                    failed++;
                }
            }
        } finally {
            MDC.remove(ACTION_MDC_KEY);
        }

        if (failed == 0) {
            log.info("Published {} outbox event(s) to {}", batch.size(), TOPIC);
        } else {
            log.warn("Published {} of {} outbox event(s) to {}, {} will be retried in the next cycle",
                    batch.size() - failed, batch.size(), TOPIC, failed);
        }
    }

    private boolean publish(OutboxEvent outboxEvent, boolean firstFailure) {
        if (outboxEvent.getCorrelationId() != null) {
            MDC.put(CORRELATION_ID_MDC_KEY, outboxEvent.getCorrelationId());
        }
        try {
            kafkaTemplate.send(toRecord(outboxEvent)).get(5, TimeUnit.SECONDS);
            outboxEvent.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(outboxEvent);
            log.debug("Published outbox event {} ({}) for order {}",
                    outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateId());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing outbox event {}", outboxEvent.getId());
            return false;
        } catch (Exception ex) {
            if (firstFailure) {
                log.warn("Failed to publish outbox event {} ({}) for order {}",
                        outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateId(), ex);
            } else {
                log.debug("Failed to publish outbox event {}: {}", outboxEvent.getId(), ex.toString());
            }
            return false;
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent outboxEvent) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, outboxEvent.getAggregateId().toString(), outboxEvent.getPayload());
        if (outboxEvent.getCorrelationId() != null) {
            record.headers().add(CORRELATION_ID_HEADER, outboxEvent.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

}
