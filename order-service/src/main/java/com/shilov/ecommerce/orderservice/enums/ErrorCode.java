package com.shilov.ecommerce.orderservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    ORDER_NOT_FOUND_ERROR(3001, "Order was not found"),
    PRODUCT_UNAVAILABLE_ERROR(3002, "One or more products are not available"),
    MIXED_CURRENCIES_ERROR(3003, "All items in an order must use the same currency"),
    PRODUCT_SERVICE_UNAVAILABLE_ERROR(3004, "Product service is unavailable"),
    PAYMENT_SERVICE_UNAVAILABLE_ERROR(3005, "Payment service is unavailable"),
    INSUFFICIENT_STOCK_ERROR(3006, "Insufficient stock for one or more products"),
    PRODUCT_NOT_FOUND_ERROR(3007, "Product was not found"),
    STOCK_RELEASE_FAILED_ERROR(3008, "Stock release failed - manual intervention required"),
    STOCK_CONFIRMATION_FAILED_ERROR(3009, "Stock confirmation failed"),
    REFUND_FAILED_ERROR(3010, "Refund failed - manual intervention required");

    private final int code;
    private final String message;
}
