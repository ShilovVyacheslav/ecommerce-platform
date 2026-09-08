package com.shilov.ecommerce.orderservice.client;

import com.shilov.ecommerce.config.props.InternalApiProperties;
import com.shilov.ecommerce.orderservice.dto.ChargeRequestDto;
import com.shilov.ecommerce.orderservice.dto.client.PaymentResponseDto;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

import static com.shilov.ecommerce.constants.Constants.INTERNAL_API_KEY_HEADER;
import static com.shilov.ecommerce.constants.Constants.INTERNAL_VERSION_V1;

@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final RestClient paymentServiceRestClient;
    private final InternalApiProperties internalApiProperties;

    public PaymentResponseDto charge(UUID orderId, BigDecimal amount, String currency) {
        try {
            return paymentServiceRestClient
                    .post()
                    .uri(INTERNAL_VERSION_V1 + "/payments")
                    .header(INTERNAL_API_KEY_HEADER, internalApiProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ChargeRequestDto.builder()
                            .orderId(orderId)
                            .amount(amount)
                            .currency(currency)
                            .build())
                    .retrieve()
                    .body(PaymentResponseDto.class);
        } catch (RestClientException ex) {
            throw SagaStepException.paymentServiceUnavailable(ex);
        }
    }

    public void refund(UUID orderId) {
        try {
            paymentServiceRestClient
                    .post()
                    .uri(INTERNAL_VERSION_V1 + "/payments/{id}/refund", orderId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiProperties.getApiKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw SagaStepException.refundFailed(ex);
        }
    }

}
