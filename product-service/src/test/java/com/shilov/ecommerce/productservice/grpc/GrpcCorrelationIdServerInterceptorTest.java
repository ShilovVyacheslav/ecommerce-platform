package com.shilov.ecommerce.productservice.grpc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GrpcCorrelationIdServerInterceptor")
public class GrpcCorrelationIdServerInterceptorTest {

    private static final String RESERVE_STOCK = "ecommerce.product.v1.ProductService/ReserveStock";
    private static final String HEALTH_CHECK = "grpc.health.v1.Health/Check";

    private final GrpcCorrelationIdServerInterceptor interceptor = new GrpcCorrelationIdServerInterceptor();
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("com.shilov.ecommerce.access");
    private final ListAppender<ILoggingEvent> accessLog = new ListAppender<>();
    private final Map<String, String> mdcInsideHandler = new HashMap<>();
    private final Metadata closingTrailers = new Metadata();

    @BeforeEach
    void captureAccessLog() {
        accessLog.start();
        accessLogger.addAppender(accessLog);
        accessLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void releaseAccessLog() {
        accessLogger.detachAppender(accessLog);
        MDC.clear();
    }

    @Test
    @DisplayName("Valid x-correlation-id -> visible to the service with the action, MDC cleaned afterwards")
    void interceptCall_ValidCorrelationId_PutsContextIntoMdc() {
        ServerCall<String, String> call = call(RESERVE_STOCK);

        interceptor.interceptCall(call, metadata("corr-from-order-service"), handler(Status.OK)).onHalfClose();

        assertThat(mdcInsideHandler)
                .containsEntry(CORRELATION_ID_MDC_KEY, "corr-from-order-service")
                .containsEntry(ACTION_MDC_KEY, "grpc:ProductService/ReserveStock");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Unsafe x-correlation-id -> replaced with a generated one")
    void interceptCall_UnsafeCorrelationId_ReplacesIt() {
        interceptor.interceptCall(call(RESERVE_STOCK), metadata("bad id forged"), handler(Status.OK)).onHalfClose();

        assertThat(mdcInsideHandler.get(CORRELATION_ID_MDC_KEY)).isNotBlank().isNotEqualTo("bad id forged");
    }

    @Test
    @DisplayName("Business outcome (FAILED_PRECONDITION) -> one INFO access line with status, duration and reason")
    void interceptCall_BusinessRejection_LogsInfo() {
        ServerCall<String, String> call = call(RESERVE_STOCK);
        Status rejection = Status.FAILED_PRECONDITION.withDescription("Insufficient stock for one or more products");

        interceptor.interceptCall(call, metadata("corr-1"), handler(rejection)).onHalfClose();

        verify(call).close(rejection, closingTrailers);
        assertThat(accessLog.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).matches(
                    "gRPC grpc:ProductService/ReserveStock -> FAILED_PRECONDITION in \\d+ ms: Insufficient stock.*");
            assertThat(event.getMDCPropertyMap()).containsEntry(CORRELATION_ID_MDC_KEY, "corr-1");
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Status.Code.class, names = {"INTERNAL", "UNAVAILABLE", "DEADLINE_EXCEEDED", "UNAUTHENTICATED"})
    @DisplayName("Failure on our side or an auth problem -> WARN access line")
    void interceptCall_ServerSideFailure_LogsWarn(Status.Code code) {
        interceptor.interceptCall(call(RESERVE_STOCK), metadata("corr-2"), handler(Status.fromCode(code))).onHalfClose();

        assertThat(accessLog.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    @DisplayName("Health check -> not access-logged")
    void interceptCall_HealthCheck_IsNotLogged() {
        interceptor.interceptCall(call(HEALTH_CHECK), new Metadata(), handler(Status.OK)).onHalfClose();

        assertThat(accessLog.list).isEmpty();
    }

    private ServerCallHandler<String, String> handler(Status closingStatus) {
        return (serverCall, headers) -> new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() {
                mdcInsideHandler.putAll(MDC.getCopyOfContextMap());
                serverCall.close(closingStatus, closingTrailers);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<String, String> call(String fullMethodName) {
        ServerCall<String, String> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethodName)
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build());
        return call;
    }

    private static Metadata metadata(String correlationId) {
        Metadata metadata = new Metadata();
        metadata.put(GrpcCorrelationIdServerInterceptor.CORRELATION_ID_HEADER, correlationId);
        return metadata;
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return "";
        }

    }

}
