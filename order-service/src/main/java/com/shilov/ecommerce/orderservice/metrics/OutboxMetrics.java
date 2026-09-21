package com.shilov.ecommerce.orderservice.metrics;

import com.shilov.ecommerce.orderservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("outbox.events.unpublished", outboxEventRepository,
                OutboxEventRepository::countByPublishedAtIsNull);
    }

}
