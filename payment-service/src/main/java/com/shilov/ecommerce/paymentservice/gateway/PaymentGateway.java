package com.shilov.ecommerce.paymentservice.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public interface PaymentGateway {

    GatewayResult charge(BigDecimal amount, String currency);

    void refund(String providerReference);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayResult {
        private boolean approved;
        private String failureReason;
        private String providerReference;
    }

}
