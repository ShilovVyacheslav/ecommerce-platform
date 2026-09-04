package com.shilov.ecommerce.paymentservice.service.impl;

import com.shilov.ecommerce.enums.payment.PaymentStatus;
import com.shilov.ecommerce.paymentservice.dto.ChargeRequestDto;
import com.shilov.ecommerce.paymentservice.dto.PaymentResponseDto;
import com.shilov.ecommerce.paymentservice.entity.Payment;
import com.shilov.ecommerce.paymentservice.exception.PaymentException;
import com.shilov.ecommerce.paymentservice.gateway.PaymentGateway;
import com.shilov.ecommerce.paymentservice.gateway.PaymentGateway.GatewayResult;
import com.shilov.ecommerce.paymentservice.mapper.PaymentMapper;
import com.shilov.ecommerce.paymentservice.repository.PaymentRepository;
import com.shilov.ecommerce.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGateway paymentGateway;

    @Override
    @Transactional
    public PaymentResponseDto charge(ChargeRequestDto chargeRequestDto) {
        Optional<Payment> existing = paymentRepository.findByOrderId(chargeRequestDto.getOrderId());
        if (existing.isPresent()) {
            return paymentMapper.toDto(existing.get());
        }

        GatewayResult gatewayResult = paymentGateway
                .charge(chargeRequestDto.getAmount(), chargeRequestDto.getCurrency());

        Payment payment = Payment.builder()
                .orderId(chargeRequestDto.getOrderId())
                .amount(chargeRequestDto.getAmount())
                .currency(chargeRequestDto.getCurrency().toUpperCase())
                .status(gatewayResult.isApproved() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED)
                .failureReason(gatewayResult.getFailureReason())
                .build();

        return paymentMapper.toDto(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponseDto refund(UUID orderId) {
        Payment payment = findOrThrow(orderId);
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment = paymentRepository.save(payment);
        }
        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto getByOrderId(UUID orderId) {
        return paymentMapper.toDto(findOrThrow(orderId));
    }

    private Payment findOrThrow(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow(PaymentException::notFound);
    }
}
