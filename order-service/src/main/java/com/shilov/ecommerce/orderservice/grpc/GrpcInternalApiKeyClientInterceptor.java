package com.shilov.ecommerce.orderservice.grpc;

import com.shilov.ecommerce.config.props.InternalApiProperties;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

import static com.shilov.ecommerce.constants.Constants.GRPC_INTERNAL_API_KEY_HEADER;

@Component
@RequiredArgsConstructor
@GlobalClientInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GrpcInternalApiKeyClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of(GRPC_INTERNAL_API_KEY_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    private final InternalApiProperties internalApiProperties;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(API_KEY_HEADER, internalApiProperties.getApiKey());
                super.start(responseListener, headers);
            }
        };
    }

}
