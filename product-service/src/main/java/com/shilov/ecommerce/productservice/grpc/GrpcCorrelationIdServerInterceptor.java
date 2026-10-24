package com.shilov.ecommerce.productservice.grpc;

import com.shilov.ecommerce.logging.CorrelationIds;
import com.shilov.ecommerce.logging.LogActions;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.GRPC_CORRELATION_ID_HEADER;
import static net.logstash.logback.argument.StructuredArguments.v;

@Component
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GrpcCorrelationIdServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> CORRELATION_ID_HEADER =
            Metadata.Key.of(GRPC_CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    private static final Logger accessLog = LoggerFactory.getLogger("com.shilov.ecommerce.access");

    private static final String HEALTH_SERVICE = "grpc.health.v1.Health";
    private static final int MAX_DESCRIPTION_LENGTH = 256;
    private static final Set<Status.Code> WARN_STATUSES = EnumSet.of(
            Status.Code.UNKNOWN, Status.Code.INTERNAL, Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED,
            Status.Code.DATA_LOSS, Status.Code.UNIMPLEMENTED, Status.Code.UNAUTHENTICATED,
            Status.Code.PERMISSION_DENIED);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String correlationId = CorrelationIds.acceptOrGenerate(headers.get(CORRELATION_ID_HEADER));
        String action = LogActions.grpc(call.getMethodDescriptor().getFullMethodName());
        boolean accessLogged = !HEALTH_SERVICE.equals(call.getMethodDescriptor().getServiceName());
        long startNanos = System.nanoTime();

        ServerCall<ReqT, RespT> loggingCall = new SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (accessLogged) {
                    runInContext(correlationId, action, () -> logCall(action, status, startNanos));
                }
                super.close(status, trailers);
            }
        };

        ServerCall.Listener<ReqT> delegate = callInContext(correlationId, action,
                () -> next.startCall(loggingCall, headers));

        return new SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                runInContext(correlationId, action, () -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                runInContext(correlationId, action, super::onHalfClose);
            }

            @Override
            public void onCancel() {
                runInContext(correlationId, action, super::onCancel);
            }

            @Override
            public void onComplete() {
                runInContext(correlationId, action, super::onComplete);
            }

            @Override
            public void onReady() {
                runInContext(correlationId, action, super::onReady);
            }
        };
    }

    private static void logCall(String action, Status status, long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        Level level = WARN_STATUSES.contains(status.getCode()) ? Level.WARN : Level.INFO;
        String description = status.getDescription();
        accessLog.atLevel(level).log("gRPC {} -> {} in {} ms{}",
                action,
                v("grpcStatus", status.getCode()),
                v("durationMs", durationMs),
                description == null ? "" : ": " + truncate(description));
    }

    private static String truncate(String text) {
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    private static void runInContext(String correlationId, String action, Runnable callback) {
        callInContext(correlationId, action, () -> {
            callback.run();
            return null;
        });
    }

    private static <T> T callInContext(String correlationId, String action, Supplier<T> callback) {
        String previousCorrelationId = MDC.get(CORRELATION_ID_MDC_KEY);
        String previousAction = MDC.get(ACTION_MDC_KEY);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(ACTION_MDC_KEY, action);
        try {
            return callback.get();
        } finally {
            restore(CORRELATION_ID_MDC_KEY, previousCorrelationId);
            restore(ACTION_MDC_KEY, previousAction);
        }
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

}
