package com.shilov.ecommerce.paymentservice.exception;

import com.shilov.ecommerce.enums.ErrorType;
import com.shilov.ecommerce.enums.ServiceName;
import com.shilov.ecommerce.exception.ApplicationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

import static com.shilov.ecommerce.enums.ErrorType.NOT_FOUND_ERROR;
import static com.shilov.ecommerce.enums.ServiceName.PAYMENT_SERVICE;
import static com.shilov.ecommerce.paymentservice.enums.ErrorCode.PAYMENT_NOT_FOUND_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentException extends ApplicationException {

    private PaymentException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static PaymentException notFound() {
        return new PaymentException(
                PAYMENT_NOT_FOUND_ERROR.getMessage(), PAYMENT_NOT_FOUND_ERROR.getCode(),
                NOT_FOUND_ERROR, PAYMENT_SERVICE, HttpStatus.NOT_FOUND);
    }
}
