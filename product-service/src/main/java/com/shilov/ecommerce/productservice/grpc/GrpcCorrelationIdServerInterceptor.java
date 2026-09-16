package com.shilov.ecommerce.productservice.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.GRPC_CORRELATION_ID_HEADER;

@Component
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GrpcCorrelationIdServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> CORRELATION_ID_HEADER =
            Metadata.Key.of(GRPC_CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String correlationId = headers.get(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;

        ServerCall.Listener<ReqT> delegate = Contexts.interceptCall(Context.current(), call, headers, next);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onHalfClose() {
                MDC.put(CORRELATION_ID_MDC_KEY, finalCorrelationId);
                try {
                    super.onHalfClose();
                } finally {
                    MDC.remove(CORRELATION_ID_MDC_KEY);
                }
            }
        };
    }

}
