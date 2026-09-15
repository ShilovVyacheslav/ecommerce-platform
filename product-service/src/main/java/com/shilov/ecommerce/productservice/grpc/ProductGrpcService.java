package com.shilov.ecommerce.productservice.grpc;

import com.shilov.ecommerce.grpc.product.v1.GetProductRequest;
import com.shilov.ecommerce.grpc.product.v1.OrderReference;
import com.shilov.ecommerce.grpc.product.v1.ProductServiceGrpc;
import com.shilov.ecommerce.grpc.product.v1.ProductSnapshot;
import com.shilov.ecommerce.grpc.product.v1.ReservationItem;
import com.shilov.ecommerce.grpc.product.v1.ReservationLine;
import com.shilov.ecommerce.grpc.product.v1.ReservationResponse;
import com.shilov.ecommerce.grpc.product.v1.ReservationStatus;
import com.shilov.ecommerce.grpc.product.v1.ReserveStockRequest;
import com.shilov.ecommerce.productservice.dto.ProductDto;
import com.shilov.ecommerce.productservice.dto.ReservationItemDto;
import com.shilov.ecommerce.productservice.dto.ReservationRequestDto;
import com.shilov.ecommerce.productservice.dto.ReservationResponseDto;
import com.shilov.ecommerce.productservice.service.InventoryService;
import com.shilov.ecommerce.productservice.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;
    private final InventoryService inventoryService;

    @Override
    public void getProduct(GetProductRequest request, StreamObserver<ProductSnapshot> responseObserver) {
        requireNotBlank(request.getProductId(), "product_id");

        ProductDto productDto = productService.getProductById(request.getProductId());

        responseObserver.onNext(ProductSnapshot.newBuilder()
                .setId(productDto.getId())
                .setName(productDto.getName())
                .setPrice(productDto.getPrice().toPlainString())
                .setCurrency(productDto.getCurrency())
                .setActive(Boolean.TRUE.equals(productDto.getActive()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReservationResponse> responseObserver) {
        requireNotBlank(request.getOrderId(), "order_id");
        if (request.getItemsCount() == 0) {
            throw Status.INVALID_ARGUMENT.withDescription("items must not be empty").asRuntimeException();
        }

        List<ReservationItemDto> items = request.getItemsList().stream()
                .map(this::toReservationItemDto)
                .toList();

        ReservationResponseDto reservationResponseDto = inventoryService.reserve(ReservationRequestDto.builder()
                .orderId(request.getOrderId())
                .items(items)
                .build());

        respond(reservationResponseDto, responseObserver);
    }

    @Override
    public void confirmReservation(OrderReference request, StreamObserver<ReservationResponse> responseObserver) {
        requireNotBlank(request.getOrderId(), "order_id");
        respond(inventoryService.confirm(request.getOrderId()), responseObserver);
    }

    @Override
    public void releaseReservation(OrderReference request, StreamObserver<ReservationResponse> responseObserver) {
        requireNotBlank(request.getOrderId(), "order_id");
        respond(inventoryService.release(request.getOrderId()), responseObserver);
    }

    private void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription(fieldName + " must not be blank").asRuntimeException();
        }
    }

    private ReservationStatus toProtoStatus(com.shilov.ecommerce.productservice.enums.ReservationStatus status) {
        return switch (status) {
            case RESERVED -> ReservationStatus.RESERVED;
            case CONFIRMED -> ReservationStatus.CONFIRMED;
            case RELEASED -> ReservationStatus.RELEASED;
        };
    }

    private void respond(ReservationResponseDto reservationResponseDto,
                         StreamObserver<ReservationResponse> responseObserver) {
        ReservationResponse.Builder builder = ReservationResponse.newBuilder()
                .setReservationId(reservationResponseDto.getReservationId());
        reservationResponseDto.getItems().forEach(line -> builder.addItems(ReservationLine.newBuilder()
                .setProductId(line.getProductId())
                .setQuantity(line.getQuantity())
                .setStatus(toProtoStatus(line.getStatus()))
                .build()));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private ReservationItemDto toReservationItemDto(ReservationItem item) {
        requireNotBlank(item.getProductId(), "items[].product_id");
        if (item.getQuantity() < 1) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("items[].quantity must be >= 1")
                    .asRuntimeException();
        }
        return ReservationItemDto.builder()
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .build();
    }

}
