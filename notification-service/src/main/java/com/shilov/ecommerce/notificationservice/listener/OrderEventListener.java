package com.shilov.ecommerce.notificationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shilov.ecommerce.events.OrderEventPayload;
import com.shilov.ecommerce.notificationservice.service.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final OrderNotificationService orderNotificationService;

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void onOrderEvent(String message) throws JsonProcessingException {
        try {
            orderNotificationService.handle(objectMapper.readValue(message, OrderEventPayload.class));
        } catch (Exception ex) {
            log.error("Failed to process order event: {}", message, ex);
            throw ex;
        }
    }
}
