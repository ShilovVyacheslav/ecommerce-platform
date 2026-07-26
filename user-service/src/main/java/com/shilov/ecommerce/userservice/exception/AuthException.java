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
import static com.shilov.ecommerce.ecommerceplatform.enums.ServiceName.AUTH_SERVICE;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.ACCESS_DENIED_ERROR;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.JWT_AUTH_ERROR;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.JWT_SYSTEM_ERROR;
import static com.shilov.ecommerce.userservice.enums.ErrorCode.INVALID_CREDENTIALS_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthException extends ApplicationException {

    private AuthException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static AuthException invalidCredentials() {
        return new AuthException(
                INVALID_CREDENTIALS_ERROR.getMessage(),
                INVALID_CREDENTIALS_ERROR.getCode(),
                VALIDATION_ERROR,
                AUTH_SERVICE,
                HttpStatus.UNAUTHORIZED
        );
    }

    public static AuthException accessDenied() {
        return new AuthException(
                ACCESS_DENIED_ERROR.getMessage(),
                ACCESS_DENIED_ERROR.getCode(),
                VALIDATION_ERROR,
                AUTH_SERVICE,
                HttpStatus.FORBIDDEN
        );
    }

    public static AuthException jwtAuth() {
        return new AuthException(
                JWT_AUTH_ERROR.getMessage(),
                JWT_AUTH_ERROR.getCode(),
                VALIDATION_ERROR,
                AUTH_SERVICE,
                HttpStatus.UNAUTHORIZED
        );
    }

    public static AuthException jwtSystem() {
        return new AuthException(
                JWT_SYSTEM_ERROR.getMessage(),
                JWT_SYSTEM_ERROR.getCode(),
                VALIDATION_ERROR,
                AUTH_SERVICE,
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

}
