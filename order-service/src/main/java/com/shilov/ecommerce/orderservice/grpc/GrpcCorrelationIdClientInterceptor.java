package com.shilov.ecommerce.orderservice.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.GRPC_CORRELATION_ID_HEADER;

@Component
@GlobalClientInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GrpcCorrelationIdClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> CORRELATION_ID_HEADER =
            Metadata.Key.of(GRPC_CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
                if (correlationId != null) {
                    headers.put(CORRELATION_ID_HEADER, correlationId);
                }
                super.start(responseListener, headers);
            }
        };
    }

}
