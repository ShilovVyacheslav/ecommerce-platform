package com.shilov.ecommerce.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponseDto {
    private UUID id;
    private OrderStatus status;
    private List<OrderItemResponseDto> items;
    private BigDecimal totalAmount;
    private String currency;
    private String failureReason;
    private LocalDateTime createdAt;
}
