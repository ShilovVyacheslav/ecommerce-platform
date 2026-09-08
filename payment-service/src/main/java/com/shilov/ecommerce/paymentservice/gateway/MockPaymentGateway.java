package com.shilov.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final BigDecimal DECLINE_INSUFFICIENT_FUNDS = new BigDecimal("13.13");
    private static final BigDecimal DECLINE_CARD_EXPIRED = new BigDecimal("14.14");
    private static final BigDecimal DECLINE_FRAUD_SUSPECTED = new BigDecimal("15.15");

    @Override
    public GatewayResult charge(BigDecimal amount, String currency) {
        String providerReference = "mock_" + UUID.randomUUID();

        if (amount.compareTo(DECLINE_INSUFFICIENT_FUNDS) == 0) {
            return new GatewayResult(false, "insufficient_funds", null);
        }
        if (amount.compareTo(DECLINE_CARD_EXPIRED) == 0) {
            return new GatewayResult(false, "card_expired", null);
        }
        if (amount.compareTo(DECLINE_FRAUD_SUSPECTED) == 0) {
            return new GatewayResult(false, "fraud_suspected", null);
        }

        return new GatewayResult(true, null, providerReference);
    }

    @Override
    public void refund(String providerReference) {
        log.info("Mock refund issued for provider reference: {}", providerReference);
    }
}
