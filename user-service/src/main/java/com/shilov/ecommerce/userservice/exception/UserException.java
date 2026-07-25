package com.shilov.ecommerce.userservice.exception;

import com.shilov.ecommerce.ecommerceplatform.enums.ErrorType;
import com.shilov.ecommerce.ecommerceplatform.enums.ServiceName;

import com.shilov.ecommerce.ecommerceplatform.exception.ApplicationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

import static com.shilov.ecommerce.ecommerceplatform.enums.ErrorType.VALIDATION_ERROR;
import static com.shilov.ecommerce.ecommerceplatform.enums.ServiceName.USER_SERVICE;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.USER_ALREADY_EXIST_ERROR;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.USER_NOT_FOUND_BY_ID_ERROR;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.USER_NOT_FOUND_BY_USERNAME_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserException extends ApplicationException {

    private UserException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static UserException userAlreadyExists(String identifier) {
        return new UserException(
                String.format(USER_ALREADY_EXIST_ERROR.getMessage(), identifier),
                USER_ALREADY_EXIST_ERROR.getCode(),
                VALIDATION_ERROR,
                USER_SERVICE,
                HttpStatus.CONFLICT
        );
    }

    public static UserException userNotFound(Long id) {
        return new UserException(
                String.format(USER_NOT_FOUND_BY_ID_ERROR.getMessage(), id),
                USER_NOT_FOUND_BY_ID_ERROR.getCode(),
                VALIDATION_ERROR,
                USER_SERVICE,
                HttpStatus.NOT_FOUND
        );
    }

    public static UserException userNotFound(String username) {
        return new UserException(
                String.format(USER_NOT_FOUND_BY_USERNAME_ERROR.getMessage(), username),
                USER_NOT_FOUND_BY_USERNAME_ERROR.getCode(),
                VALIDATION_ERROR,
                USER_SERVICE,
                HttpStatus.NOT_FOUND
        );
    }

}
