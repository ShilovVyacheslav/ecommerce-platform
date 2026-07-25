package com.shilov.ecommerce.userservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_CREDENTIALS_ERROR("Invalid username or password", 401),
    ACCESS_DENIED_ERROR("Access denied", 403),
    JWT_AUTH_ERROR("Authentication failed due to invalid or expired token", 401),
    JWT_SYSTEM_ERROR("Internal authentication system error", 500),

    USER_ALREADY_EXIST_ERROR("User '%s' already exists", 409),
    USER_NOT_FOUND_BY_ID_ERROR("User not found with id: %s", 404),
    USER_NOT_FOUND_BY_USERNAME_ERROR("User not found with username: %s", 404),

    VALIDATION_FAILED_ERROR_CODE("validation failed", 400),
    INVALID_REQUEST_PARAM_ERROR_CODE("invalid request param", 400);

    private final String message;
    private final int code;
}
