package com.shilov.ecommerce.orderservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shilov.ecommerce.events.OrderEventPayload;
import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.entity.OutboxEvent;
import com.shilov.ecommerce.orderservice.enums.OutboxEventType;
import com.shilov.ecommerce.orderservice.repository.OrderRepository;
import com.shilov.ecommerce.orderservice.repository.OutboxEventRepository;
import com.shilov.ecommerce.orderservice.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Order saveAndPublish(Order order, OutboxEventType outboxEventType) {
        Order savedOrder = orderRepository.save(order);

        OrderEventPayload payload = OrderEventPayload.builder()
                .orderId(savedOrder.getId())
                .userId(savedOrder.getUserId())
                .status(savedOrder.getStatus().name())
                .totalAmount(savedOrder.getTotalAmount())
                .currency(savedOrder.getCurrency())
                .failureReason(savedOrder.getFailureReason())
                .occurredAt(Instant.now())
                .build();

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize order event payload", ex);
        }

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(savedOrder.getId())
                .eventType(outboxEventType.name())
                .payload(json)
                .build());
        return savedOrder;
    }
}
