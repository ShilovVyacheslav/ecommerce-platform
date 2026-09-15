package com.shilov.ecommerce.productservice.grpc;

import com.shilov.ecommerce.exception.ApplicationException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrpcExceptionTranslator implements GrpcExceptionHandler {

    @Override
    public StatusException handleException(Throwable exception) {
        if (exception instanceof ApplicationException applicationException) {
            log.warn("ApplicationException [{}]: {}",
                    applicationException.getClass().getSimpleName(), applicationException.getMessage());
            return toGrpcStatus(applicationException).asException();
        }
        if (exception instanceof StatusException statusException) {
            return statusException;
        }
        if (exception instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus().asException();
        }
        log.error("Unhandled exception in gRPC service", exception);
        return Status.INTERNAL
                .withDescription("An unexpected error occurred")
                .withCause(exception)
                .asException();
    }

    private Status toGrpcStatus(ApplicationException exception) {
        String description = "%s (service=%s, code=%d, type=%s)".formatted(
                exception.getMessage(),
                exception.getServiceName() != null ? exception.getServiceName().name() : "UNKNOWN",
                exception.getCode(),
                exception.getErrorType());

        return baseStatusFor(exception.getStatus()).withDescription(description).withCause(exception);
    }

    private Status baseStatusFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case CONFLICT, UNPROCESSABLE_ENTITY -> Status.FAILED_PRECONDITION;
            case BAD_REQUEST -> Status.INVALID_ARGUMENT;
            case UNAUTHORIZED -> Status.UNAUTHENTICATED;
            case FORBIDDEN -> Status.PERMISSION_DENIED;
            case SERVICE_UNAVAILABLE -> Status.UNAVAILABLE;
            default -> Status.INTERNAL;
        };
    }

}
