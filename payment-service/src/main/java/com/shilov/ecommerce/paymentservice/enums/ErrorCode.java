package com.shilov.ecommerce.paymentservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    PAYMENT_NOT_FOUND_ERROR(2001, "No payment found for this order");

    private final int code;
    private final String message;
}
