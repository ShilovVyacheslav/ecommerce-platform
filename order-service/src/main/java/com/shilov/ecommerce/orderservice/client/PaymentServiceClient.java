package com.shilov.ecommerce.orderservice.client;

import com.shilov.ecommerce.config.props.InternalApiProperties;
import com.shilov.ecommerce.dto.ErrorDto;
import com.shilov.ecommerce.enums.ServiceName;
import com.shilov.ecommerce.orderservice.dto.ChargeRequestDto;
import com.shilov.ecommerce.orderservice.dto.client.PaymentResponseDto;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

import static com.shilov.ecommerce.constants.Constants.INTERNAL_API_KEY_HEADER;
import static com.shilov.ecommerce.constants.Constants.INTERNAL_VERSION_V1;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private static final int PAYMENT_NOT_FOUND_CODE = 2001;

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
        } catch (HttpClientErrorException.NotFound ex) {
            if (!isPaymentNotFound(ex)) {
                throw SagaStepException.refundFailed(ex);
            }
            log.info("No payment exists for order {} - the charge never landed, nothing to refund", orderId);
        } catch (RestClientException ex) {
            throw SagaStepException.refundFailed(ex);
        }
    }

    private static boolean isPaymentNotFound(HttpClientErrorException.NotFound ex) {
        ErrorDto errorDto;
        try {
            errorDto = ex.getResponseBodyAs(ErrorDto.class);
        } catch (RuntimeException unreadableBody) {
            return false;
        }
        return errorDto != null
                && errorDto.serviceName() == ServiceName.PAYMENT_SERVICE
                && Integer.valueOf(PAYMENT_NOT_FOUND_CODE).equals(errorDto.code());
    }

}
