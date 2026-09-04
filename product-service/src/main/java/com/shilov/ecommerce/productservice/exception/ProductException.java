package com.shilov.ecommerce.productservice.exception;

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
import static com.shilov.ecommerce.enums.ServiceName.PRODUCT_SERVICE;
import static com.shilov.ecommerce.productservice.enums.ErrorCode.INSUFFICIENT_STOCK_ERROR;
import static com.shilov.ecommerce.productservice.enums.ErrorCode.INVALID_STOCK_ADJUSTMENT_ERROR;
import static com.shilov.ecommerce.productservice.enums.ErrorCode.PRODUCT_NOT_FOUND_ERROR;
import static com.shilov.ecommerce.productservice.enums.ErrorCode.PRODUCT_SKU_CONFLICT_ERROR;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductException extends ApplicationException {

    private ProductException(String message, int code, ErrorType errorType, ServiceName serviceName, HttpStatus status) {
        super(message, code, errorType, serviceName, status);
    }

    public static ProductException notFound() {
        return new ProductException(
                PRODUCT_NOT_FOUND_ERROR.getMessage(), PRODUCT_NOT_FOUND_ERROR.getCode(),
                NOT_FOUND_ERROR, PRODUCT_SERVICE, HttpStatus.NOT_FOUND);
    }

    public static ProductException skuConflict() {
        return new ProductException(
                PRODUCT_SKU_CONFLICT_ERROR.getMessage(), PRODUCT_SKU_CONFLICT_ERROR.getCode(),
                CONFLICT_ERROR, PRODUCT_SERVICE, HttpStatus.CONFLICT);
    }

    public static ProductException insufficientStock() {
        return new ProductException(
                INSUFFICIENT_STOCK_ERROR.getMessage(), INSUFFICIENT_STOCK_ERROR.getCode(),
                CONFLICT_ERROR, PRODUCT_SERVICE, HttpStatus.CONFLICT);
    }

    public static ProductException invalidStockAdjustment() {
        return new ProductException(
                INVALID_STOCK_ADJUSTMENT_ERROR.getMessage(), INVALID_STOCK_ADJUSTMENT_ERROR.getCode(),
                CONFLICT_ERROR, PRODUCT_SERVICE, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
