package com.shilov.ecommerce.orderservice.client;

import com.shilov.ecommerce.config.props.InternalApiProperties;
import com.shilov.ecommerce.orderservice.dto.client.ProductSnapshotDto;
import com.shilov.ecommerce.orderservice.dto.client.ReservationItemRequestDto;
import com.shilov.ecommerce.orderservice.dto.client.ReservationRequestDto;
import com.shilov.ecommerce.orderservice.exception.OrderException;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static com.shilov.ecommerce.constants.Constants.API_VERSION_V1;
import static com.shilov.ecommerce.constants.Constants.INTERNAL_API_KEY_HEADER;
import static com.shilov.ecommerce.constants.Constants.INTERNAL_VERSION_V1;

@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private static final String RESERVATIONS_PATH = INTERNAL_VERSION_V1 + "/inventory/reservations";

    private final RestClient productServiceRestClient;
    private final InternalApiProperties internalApiProperties;

    public ProductSnapshotDto getProduct(String productId) {
        try {
            return productServiceRestClient
                    .get()
                    .uri(API_VERSION_V1 + "/products/{id}", productId)
                    .retrieve()
                    .body(ProductSnapshotDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw OrderException.productUnavailable();
        } catch (RestClientException ex) {
            throw SagaStepException.productServiceUnavailable(ex);
        }
    }

    public void reserve(String orderId, List<ReservationItemRequestDto> items) {
        try {
            productServiceRestClient
                    .post()
                    .uri(RESERVATIONS_PATH)
                    .header(INTERNAL_API_KEY_HEADER, internalApiProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ReservationRequestDto.builder()
                            .orderId(orderId)
                            .items(items)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict ex) {
            throw SagaStepException.insufficientStock();
        } catch (HttpClientErrorException.NotFound ex) {
            throw SagaStepException.productNotFound();
        } catch (RestClientException ex) {
            throw SagaStepException.productServiceUnavailable(ex);
        }
    }

    public void release(String orderId) {
        try {
            productServiceRestClient
                    .post()
                    .uri(RESERVATIONS_PATH + "/{id}/release", orderId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiProperties.getApiKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw SagaStepException.stockReleaseFailed(ex);
        }
    }

    public void confirm(String orderId) {
        try {
            productServiceRestClient
                    .post()
                    .uri(RESERVATIONS_PATH + "/{id}/confirm", orderId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiProperties.getApiKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw SagaStepException.stockConfirmationFailed(ex);
        }
    }
}
