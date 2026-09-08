package com.shilov.ecommerce.orderservice.publisher;

import com.shilov.ecommerce.orderservice.entity.OutboxEvent;
import com.shilov.ecommerce.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisher {

    private static final String TOPIC = "order-events";
    private static final int BATCH_SIZE = 20;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedRate = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublishedBatch(BATCH_SIZE);

        for (OutboxEvent outboxEvent : batch) {
            try {
                kafkaTemplate.send(TOPIC, outboxEvent.getAggregateId().toString(), outboxEvent.getPayload())
                        .get(5, TimeUnit.SECONDS);
                outboxEvent.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(outboxEvent);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {} - will retry next cycle", outboxEvent.getId(), ex);
            }
        }
    }
}
