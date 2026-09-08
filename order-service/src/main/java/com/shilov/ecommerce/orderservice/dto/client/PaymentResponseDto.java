package com.shilov.ecommerce.orderservice.dto.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shilov.ecommerce.enums.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PaymentResponseDto {
    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String failureReason;
    private LocalDateTime createdAt;
}
