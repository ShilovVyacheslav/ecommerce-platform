package com.shilov.ecommerce.productservice.grpc;

import com.shilov.ecommerce.config.props.InternalApiProperties;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import static com.shilov.ecommerce.constants.Constants.GRPC_INTERNAL_API_KEY_HEADER;

@Component
@RequiredArgsConstructor
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GrpcInternalApiKeyInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of(GRPC_INTERNAL_API_KEY_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    private static final Set<String> EXEMPT_SERVICES = Set.of(
            "grpc.health.v1.Health",
            "grpc.reflection.v1.ServerReflection",
            "grpc.reflection.v1alpha.ServerReflection"
    );

    private final InternalApiProperties internalApiProperties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        if (EXEMPT_SERVICES.contains(call.getMethodDescriptor().getServiceName())) {
            return Contexts.interceptCall(Context.current(), call, headers, next);
        }

        String provided = headers.get(API_KEY_HEADER);
        String expected = internalApiProperties.getApiKey();

        boolean valid = provided != null && expected != null && !expected.isBlank() && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid internal API key"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return Contexts.interceptCall(Context.current(), call, headers, next);
    }

}
