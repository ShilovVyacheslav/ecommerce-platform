package com.shilov.ecommerce.productservice.exception;

import com.shilov.ecommerce.enums.ErrorType;
import com.shilov.ecommerce.enums.ServiceName;
import com.shilov.ecommerce.exception.ApplicationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

import static com.shilov.ecommerce.enums.ErrorType.NOT_FOUND_ERROR;
import static com.shilov.ecommerce.enums.ServiceName.PRODUCT_SERVICE;
import static com.shilov.ecommerce.productservice.enums.ErrorCode.RESERVATION_NOT_FOUND_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InventoryException extends ApplicationException {

    private InventoryException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static InventoryException reservationNotFound() {
        return new InventoryException(
                RESERVATION_NOT_FOUND_ERROR.getMessage(), RESERVATION_NOT_FOUND_ERROR.getCode(),
                NOT_FOUND_ERROR, PRODUCT_SERVICE, HttpStatus.NOT_FOUND);
    }
}
