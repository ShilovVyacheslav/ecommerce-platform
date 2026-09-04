package com.shilov.ecommerce.handler;

import com.shilov.ecommerce.dto.ErrorDto;
import com.shilov.ecommerce.enums.ErrorType;
import com.shilov.ecommerce.exception.ApplicationException;
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
    public ResponseEntity<ErrorDto> handleApplicationException(ApplicationException ex) {
        log.warn("ApplicationException [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
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
        ErrorDto errorDto = new ErrorDto(0, ex.getMessage(), ErrorType.VALIDATION_ERROR, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ErrorDto errorDto = new ErrorDto(
                0,
                "An unexpected error occured",
                ErrorType.INTERNAL_ERROR,
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }

}
