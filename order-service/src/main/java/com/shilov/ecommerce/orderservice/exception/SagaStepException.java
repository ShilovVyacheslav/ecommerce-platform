package com.shilov.ecommerce.orderservice.exception;

import com.shilov.ecommerce.enums.ErrorType;
import com.shilov.ecommerce.enums.ServiceName;
import com.shilov.ecommerce.exception.ApplicationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

import static com.shilov.ecommerce.enums.ErrorType.CONFLICT_ERROR;
import static com.shilov.ecommerce.enums.ErrorType.NOT_FOUND_ERROR;
import static com.shilov.ecommerce.enums.ErrorType.SYSTEM_ERROR;
import static com.shilov.ecommerce.enums.ServiceName.ORDER_SERVICE;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.INSUFFICIENT_STOCK_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.PAYMENT_SERVICE_UNAVAILABLE_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.PRODUCT_NOT_FOUND_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.PRODUCT_SERVICE_UNAVAILABLE_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.REFUND_FAILED_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.STOCK_CONFIRMATION_FAILED_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.STOCK_RELEASE_FAILED_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SagaStepException extends ApplicationException {

    private SagaStepException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static SagaStepException productServiceUnavailable() {
        return productServiceUnavailable(null);
    }

    public static SagaStepException productServiceUnavailable(Throwable cause) {
        return withCause(cause, new SagaStepException(
                PRODUCT_SERVICE_UNAVAILABLE_ERROR.getMessage(), PRODUCT_SERVICE_UNAVAILABLE_ERROR.getCode(),
                SYSTEM_ERROR, ORDER_SERVICE, HttpStatus.SERVICE_UNAVAILABLE));
    }

    public static SagaStepException paymentServiceUnavailable(Throwable cause) {
        return withCause(cause, new SagaStepException(
                PAYMENT_SERVICE_UNAVAILABLE_ERROR.getMessage(), PAYMENT_SERVICE_UNAVAILABLE_ERROR.getCode(),
                SYSTEM_ERROR, ORDER_SERVICE, HttpStatus.SERVICE_UNAVAILABLE));
    }

    public static SagaStepException insufficientStock() {
        return new SagaStepException(
                INSUFFICIENT_STOCK_ERROR.getMessage(), INSUFFICIENT_STOCK_ERROR.getCode(),
                CONFLICT_ERROR, ORDER_SERVICE, HttpStatus.CONFLICT);
    }

    public static SagaStepException productNotFound() {
        return new SagaStepException(
                PRODUCT_NOT_FOUND_ERROR.getMessage(), PRODUCT_NOT_FOUND_ERROR.getCode(),
                NOT_FOUND_ERROR, ORDER_SERVICE, HttpStatus.NOT_FOUND);
    }

    public static SagaStepException stockReleaseFailed(Throwable cause) {
        return withCause(cause, new SagaStepException(
                STOCK_RELEASE_FAILED_ERROR.getMessage(), STOCK_RELEASE_FAILED_ERROR.getCode(),
                SYSTEM_ERROR, ORDER_SERVICE, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    public static SagaStepException stockConfirmationFailed(Throwable cause) {
        return withCause(cause, new SagaStepException(
                STOCK_CONFIRMATION_FAILED_ERROR.getMessage(), STOCK_CONFIRMATION_FAILED_ERROR.getCode(),
                SYSTEM_ERROR, ORDER_SERVICE, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    public static SagaStepException refundFailed(Throwable cause) {
        return withCause(cause, new SagaStepException(
                REFUND_FAILED_ERROR.getMessage(), REFUND_FAILED_ERROR.getCode(),
                SYSTEM_ERROR, ORDER_SERVICE, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static SagaStepException withCause(Throwable cause, SagaStepException ex) {
        if (cause != null) {
            ex.initCause(cause);
        }
        return ex;
    }
}
