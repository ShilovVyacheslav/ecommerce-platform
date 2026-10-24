package com.shilov.ecommerce.handler;

import com.shilov.ecommerce.dto.ErrorDto;
import com.shilov.ecommerce.enums.ErrorType;
import com.shilov.ecommerce.exception.ApplicationException;
import com.shilov.ecommerce.logging.RequestLogAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorDto> handleApplicationException(ApplicationException ex, HttpServletRequest request) {
        request.setAttribute(RequestLogAttributes.ERROR_CODE, ex.getCode());
        if (ex.getStatus().is5xxServerError()) {
            log.warn("Request failed with {} [{}]: {}", ex.getStatus().value(), ex.getCode(), ex.getMessage(), ex);
        } else {
            log.debug("Request rejected with {} [{}]: {}", ex.getStatus().value(), ex.getCode(), ex.getMessage());
        }
        ErrorDto errorDto = new ErrorDto(
                ex.getCode(),
                ex.getMessage(),
                ex.getErrorType(),
                ex.getServiceName(),
                ex.getDetails());
        return ResponseEntity.status(ex.getStatus()).body(errorDto);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        if (log.isDebugEnabled()) {
            log.debug("Request validation failed for fields {}", ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getField)
                    .toList());
        }
        Map<String, List<Integer>> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(fieldError -> 0, Collectors.toList())
                ));
        ErrorDto errorDto = new ErrorDto(
                0,
                "Validation failed",
                ErrorType.VALIDATION_ERROR,
                null,
                details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        log.debug("Request constraint violation: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto(0, ex.getMessage(), ErrorType.VALIDATION_ERROR, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleException(Exception ex) {
        log.error("Unhandled exception while processing the request", ex);
        ErrorDto errorDto = new ErrorDto(
                0,
                "An unexpected error occured",
                ErrorType.INTERNAL_ERROR,
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }

}
