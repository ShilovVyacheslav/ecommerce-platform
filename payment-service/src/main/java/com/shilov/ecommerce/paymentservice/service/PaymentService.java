package com.shilov.ecommerce.paymentservice.service;

import com.shilov.ecommerce.paymentservice.dto.ChargeRequestDto;
import com.shilov.ecommerce.paymentservice.dto.PaymentResponseDto;

import java.util.UUID;

public interface PaymentService {

    PaymentResponseDto charge(ChargeRequestDto chargeRequestDto);

    PaymentResponseDto refund(UUID orderId);

    PaymentResponseDto getByOrderId(UUID orderId);

}
