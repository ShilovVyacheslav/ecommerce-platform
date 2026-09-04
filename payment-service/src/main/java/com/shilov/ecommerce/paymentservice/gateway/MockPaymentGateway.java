package com.shilov.ecommerce.paymentservice.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final BigDecimal DECLINE_TRIGGER_AMOUNT = new BigDecimal("13.13");

    @Override
    public GatewayResult charge(BigDecimal amount, String currency) {
        if (amount.compareTo(DECLINE_TRIGGER_AMOUNT) == 0) {
            return GatewayResult.builder()
                    .approved(false)
                    .failureReason("Card declined by issuer (simulated)")
                    .build();
        }
        return GatewayResult.builder().approved(true).failureReason(null).build();
    }
}
