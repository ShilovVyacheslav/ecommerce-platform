package com.shilov.ecommerce.paymentservice.controller;

import com.shilov.ecommerce.paymentservice.dto.ChargeRequestDto;
import com.shilov.ecommerce.paymentservice.dto.PaymentResponseDto;
import com.shilov.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> charge(@Valid @RequestBody ChargeRequestDto chargeRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.charge(chargeRequestDto));
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<PaymentResponseDto> refund(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.refund(orderId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponseDto> getByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId));
    }
}
