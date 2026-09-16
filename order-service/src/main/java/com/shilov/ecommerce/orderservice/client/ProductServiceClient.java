package com.shilov.ecommerce.orderservice.client;

import com.shilov.ecommerce.grpc.product.v1.GetProductRequest;
import com.shilov.ecommerce.grpc.product.v1.OrderReference;
import com.shilov.ecommerce.grpc.product.v1.ProductServiceGrpc;
import com.shilov.ecommerce.grpc.product.v1.ProductSnapshot;
import com.shilov.ecommerce.grpc.product.v1.ReservationItem;
import com.shilov.ecommerce.grpc.product.v1.ReserveStockRequest;
import com.shilov.ecommerce.orderservice.dto.client.ProductSnapshotDto;
import com.shilov.ecommerce.orderservice.dto.client.ReservationItemRequestDto;
import com.shilov.ecommerce.orderservice.exception.OrderException;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub;

    public ProductSnapshotDto getProduct(String productId) {
        try {
            ProductSnapshot productSnapshot = productServiceBlockingStub.getProduct(GetProductRequest.newBuilder()
                    .setProductId(productId).build());
            return ProductSnapshotDto.builder()
                    .id(productSnapshot.getId())
                    .name(productSnapshot.getName())
                    .price(new BigDecimal(productSnapshot.getPrice()))
                    .currency(productSnapshot.getCurrency())
                    .active(productSnapshot.getActive())
                    .build();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw OrderException.productUnavailable();
            }
            throw SagaStepException.productServiceUnavailable(ex);
        }
    }

    public void reserve(String orderId, List<ReservationItemRequestDto> items) {
        try {
            productServiceBlockingStub.reserveStock(ReserveStockRequest.newBuilder()
                    .setOrderId(orderId)
                    .addAllItems(items.stream().map(this::toReservationItem).toList())
                    .build());
        } catch (StatusRuntimeException ex) {
            throw switch (ex.getStatus().getCode()) {
                case FAILED_PRECONDITION -> SagaStepException.insufficientStock();
                case NOT_FOUND -> SagaStepException.productNotFound();
                default -> SagaStepException.productServiceUnavailable(ex);
            };
        }
    }

    public void release(String orderId) {
        try {
            productServiceBlockingStub.releaseReservation(orderReference(orderId));
        } catch (StatusRuntimeException ex) {
            throw SagaStepException.stockReleaseFailed(ex);
        }
    }

    public void confirm(String orderId) {
        try {
            productServiceBlockingStub.confirmReservation(orderReference(orderId));
        } catch (StatusRuntimeException ex) {
            throw SagaStepException.stockConfirmationFailed(ex);
        }
    }

    private ReservationItem toReservationItem(ReservationItemRequestDto item) {
        return ReservationItem.newBuilder()
                .setProductId(item.getProductId())
                .setQuantity(item.getQuantity())
                .build();
    }

    private OrderReference orderReference(String orderId) {
        return OrderReference.newBuilder().setOrderId(orderId).build();
    }

}
