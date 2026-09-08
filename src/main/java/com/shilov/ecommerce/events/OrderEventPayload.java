package com.shilov.ecommerce.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class OrderEventPayload {
    private UUID orderId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String failureReason;
    private Instant occurredAt;
}
