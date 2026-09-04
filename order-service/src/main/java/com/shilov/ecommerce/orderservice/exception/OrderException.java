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
import static com.shilov.ecommerce.enums.ErrorType.VALIDATION_ERROR;
import static com.shilov.ecommerce.enums.ServiceName.ORDER_SERVICE;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.MIXED_CURRENCIES_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.ORDER_NOT_FOUND_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.PRODUCT_UNAVAILABLE_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderException extends ApplicationException {

    private OrderException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static OrderException notFound() {
        return new OrderException(
                ORDER_NOT_FOUND_ERROR.getMessage(), ORDER_NOT_FOUND_ERROR.getCode(),
                NOT_FOUND_ERROR, ORDER_SERVICE, HttpStatus.NOT_FOUND);
    }

    public static OrderException productUnavailable() {
        return new OrderException(
                PRODUCT_UNAVAILABLE_ERROR.getMessage(), PRODUCT_UNAVAILABLE_ERROR.getCode(),
                CONFLICT_ERROR, ORDER_SERVICE, HttpStatus.CONFLICT);
    }

    public static OrderException mixedCurrencies() {
        return new OrderException(
                MIXED_CURRENCIES_ERROR.getMessage(), MIXED_CURRENCIES_ERROR.getCode(),
                VALIDATION_ERROR, ORDER_SERVICE, HttpStatus.BAD_REQUEST);
    }
}
