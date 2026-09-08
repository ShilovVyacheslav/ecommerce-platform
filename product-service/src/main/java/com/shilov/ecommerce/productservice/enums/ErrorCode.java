package com.shilov.ecommerce.productservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    PRODUCT_NOT_FOUND_ERROR(1001, "Product was not found"),
    PRODUCT_SKU_CONFLICT_ERROR(1002, "A product with this SKU already exists"),
    INSUFFICIENT_STOCK_ERROR(1003, "Insufficient stock available"),
    INVALID_STOCK_ADJUSTMENT_ERROR(1004, "Stock adjustment would result in a negative quantity"),
    RESERVATION_NOT_FOUND_ERROR(1005, "No stock reservation found for this order");

    private final int code;
    private final String message;
}
