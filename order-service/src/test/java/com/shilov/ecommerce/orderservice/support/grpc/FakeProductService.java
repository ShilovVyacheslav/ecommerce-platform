package com.shilov.ecommerce.orderservice.support.grpc;

import com.shilov.ecommerce.grpc.product.v1.GetProductRequest;
import com.shilov.ecommerce.grpc.product.v1.OrderReference;
import com.shilov.ecommerce.grpc.product.v1.ProductServiceGrpc;
import com.shilov.ecommerce.grpc.product.v1.ProductSnapshot;
import com.shilov.ecommerce.grpc.product.v1.ReservationResponse;
import com.shilov.ecommerce.grpc.product.v1.ReserveStockRequest;
import com.shilov.ecommerce.orderservice.support.SagaCallJournal;
import com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step;
import com.shilov.ecommerce.orderservice.support.TestOrders;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.shilov.ecommerce.constants.Constants.GRPC_CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.GRPC_INTERNAL_API_KEY_HEADER;

/**
 * Programmable stand-in for product-service, served over a real gRPC transport so that the production
 * client stack (deadline, retry policy, health checking, global interceptors, status mapping) is exercised.
 * <p>
 * By default every product of {@link TestOrders} is in the catalog and every reservation RPC succeeds.
 */
public class FakeProductService extends ProductServiceGrpc.ProductServiceImplBase {

    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of(GRPC_INTERNAL_API_KEY_HEADER, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CORRELATION_ID_HEADER =
            Metadata.Key.of(GRPC_CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    public record ReceivedCall(String method, String apiKey, String correlationId) {}

    private final SagaCallJournal journal;
    private final ScheduledExecutorService delayedResponses = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fake-product-service-delay");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, ProductSnapshot> catalog = new ConcurrentHashMap<>();
    private final RpcBehavior reserveBehavior = new RpcBehavior();
    private final RpcBehavior confirmBehavior = new RpcBehavior();
    private final RpcBehavior releaseBehavior = new RpcBehavior();
    private final AtomicInteger reserveAttempts = new AtomicInteger();
    private final List<ReserveStockRequest> reserveRequests = new CopyOnWriteArrayList<>();
    private final List<ReceivedCall> receivedCalls = new CopyOnWriteArrayList<>();

    public FakeProductService(SagaCallJournal journal) {
        this.journal = journal;
        reset();
    }

    public void reset() {
        catalog.clear();
        addToCatalog(TestOrders.KEYBOARD_ID, TestOrders.KEYBOARD_NAME, TestOrders.KEYBOARD_UNIT_PRICE);
        addToCatalog(TestOrders.MOUSE_ID, TestOrders.MOUSE_NAME, TestOrders.MOUSE_UNIT_PRICE);
        addToCatalog(TestOrders.HUB_ID, TestOrders.HUB_NAME, TestOrders.HUB_UNIT_PRICE);
        reserveBehavior.reset();
        confirmBehavior.reset();
        releaseBehavior.reset();
        reserveAttempts.set(0);
        reserveRequests.clear();
        receivedCalls.clear();
    }

    public void removeFromCatalog(String productId) {
        catalog.remove(productId);
    }

    public void deactivate(String productId) {
        catalog.computeIfPresent(productId, (id, product) -> product.toBuilder().setActive(false).build());
    }

    public void failReserve(Status status) {
        reserveBehavior.fail(status, Integer.MAX_VALUE);
    }

    public void failReserve(Status status, int times) {
        reserveBehavior.fail(status, times);
    }

    public void delayReserve(Duration delay) {
        reserveBehavior.delay = delay;
    }

    public void failConfirm(Status status) {
        confirmBehavior.fail(status, Integer.MAX_VALUE);
    }

    public void failRelease(Status status) {
        releaseBehavior.fail(status, Integer.MAX_VALUE);
    }

    public int reserveAttempts() {
        return reserveAttempts.get();
    }

    public List<ReserveStockRequest> reserveRequests() {
        return List.copyOf(reserveRequests);
    }

    public List<ReceivedCall> receivedCalls() {
        return List.copyOf(receivedCalls);
    }

    public ServerInterceptor metadataRecorder() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                         Metadata headers,
                                                                         ServerCallHandler<ReqT, RespT> next) {
                receivedCalls.add(new ReceivedCall(call.getMethodDescriptor().getBareMethodName(),
                        headers.get(API_KEY_HEADER), headers.get(CORRELATION_ID_HEADER)));
                return next.startCall(call, headers);
            }
        };
    }

    @Override
    public void getProduct(GetProductRequest request, StreamObserver<ProductSnapshot> responseObserver) {
        ProductSnapshot product = catalog.get(request.getProductId());
        if (product == null) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Product not found").asRuntimeException());
            return;
        }
        responseObserver.onNext(product);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReservationResponse> responseObserver) {
        reserveAttempts.incrementAndGet();
        reserveRequests.add(request);
        journal.record(Step.RESERVE, UUID.fromString(request.getOrderId()));
        respond(reserveBehavior, responseObserver);
    }

    @Override
    public void confirmReservation(OrderReference request, StreamObserver<ReservationResponse> responseObserver) {
        journal.record(Step.CONFIRM, UUID.fromString(request.getOrderId()));
        respond(confirmBehavior, responseObserver);
    }

    @Override
    public void releaseReservation(OrderReference request, StreamObserver<ReservationResponse> responseObserver) {
        journal.record(Step.RELEASE, UUID.fromString(request.getOrderId()));
        respond(releaseBehavior, responseObserver);
    }

    private void respond(RpcBehavior behavior, StreamObserver<ReservationResponse> responseObserver) {
        Status failure = behavior.nextFailure();
        Runnable completion = failure != null
                ? () -> responseObserver.onError(failure.asRuntimeException())
                : () -> {
                    responseObserver.onNext(ReservationResponse.newBuilder()
                            .setReservationId(UUID.randomUUID().toString())
                            .build());
                    responseObserver.onCompleted();
                };

        if (behavior.delay.isZero()) {
            completion.run();
            return;
        }
        delayedResponses.schedule(() -> {
            try {
                completion.run();
            } catch (RuntimeException callAlreadyCancelledByClientDeadline) {
                // expected: the client gave up before the delayed response was sent
            }
        }, behavior.delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void addToCatalog(String productId, String productName, BigDecimal productPrice) {
        catalog.put(productId, ProductSnapshot.newBuilder()
                .setId(productId)
                .setName(productName)
                .setPrice(productPrice.toPlainString())
                .setCurrency(TestOrders.CURRENCY)
                .setActive(true)
                .build());
    }

    private static final class RpcBehavior {

        private volatile Status failure;
        private final AtomicInteger failuresLeft = new AtomicInteger();
        private volatile Duration delay = Duration.ZERO;

        void fail(Status status, int times) {
            failure = status;
            failuresLeft.set(times);
        }

        Status nextFailure() {
            Status currentFailure = failure;
            if (currentFailure == null || failuresLeft.getAndDecrement() <= 0) {
                return null;
            }
            return currentFailure;
        }

        void reset() {
            failure = null;
            failuresLeft.set(0);
            delay = Duration.ZERO;
        }

    }

}
