package com.shilov.ecommerce.orderservice.service.impl;

import com.shilov.ecommerce.orderservice.client.ProductServiceClient;
import com.shilov.ecommerce.orderservice.dto.OrderCreateDto;
import com.shilov.ecommerce.orderservice.dto.OrderItemCreateDto;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import com.shilov.ecommerce.orderservice.dto.client.ProductSnapshotDto;
import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.entity.OrderItem;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import com.shilov.ecommerce.orderservice.exception.OrderException;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import com.shilov.ecommerce.orderservice.mapper.OrderMapper;
import com.shilov.ecommerce.orderservice.repository.OrderRepository;
import com.shilov.ecommerce.orderservice.saga.OrderSagaOrchestrator;
import com.shilov.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final ExecutorService productLookupExecutor;

    private static final long PRODUCT_LOOKUP_TIMEOUT_SECONDS = 10;

    @Override
    public OrderResponseDto createOrder(Jwt jwt, OrderCreateDto orderCreateDto) {
        Order order = buildOrder(extractUserId(jwt), orderCreateDto);
        order = orderRepository.save(order);

        order = orderSagaOrchestrator.process(order);

        return orderMapper.toDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Jwt jwt, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, extractUserId(jwt))
                .orElseThrow(OrderException::notFound);
        return orderMapper.toDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getOrders(Jwt jwt, Pageable pageable) {
        return orderRepository.findByUserId(extractUserId(jwt), pageable).map(orderMapper::toDto);
    }

    private Order buildOrder(Long userId, OrderCreateDto orderCreateDto) {
        Order order = Order.builder().userId(userId).status(OrderStatus.CREATED).build();

        List<OrderItemCreateDto> items = orderCreateDto.getItems();
        List<CompletableFuture<ProductSnapshotDto>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(
                        () -> productServiceClient.getProduct(item.getProductId()),
                        productLookupExecutor))
                .toList();

        List<ProductSnapshotDto> products;
        try {
            products = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
                    .get(PRODUCT_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Unexpected exception during parallel product lookup", cause);
        } catch (TimeoutException ex) {
            futures.forEach(f -> f.cancel(true));
            throw SagaStepException.productServiceUnavailable(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for product lookups", ex);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = null;

        for (int i = 0; i < items.size(); ++i) {
            ProductSnapshotDto productSnapshotDto = products.get(i);
            OrderItemCreateDto orderItemCreateDto = items.get(i);

            if (!productSnapshotDto.getActive()) {
                throw OrderException.productUnavailable();
            }
            if (currency == null) {
                currency = productSnapshotDto.getCurrency();
            } else if (!currency.equals(productSnapshotDto.getCurrency())) {
                throw OrderException.mixedCurrencies();
            }

            OrderItem orderItem = OrderItem.builder()
                    .productId(productSnapshotDto.getId())
                    .productName(productSnapshotDto.getName())
                    .quantity(orderItemCreateDto.getQuantity())
                    .unitPrice(productSnapshotDto.getPrice())
                    .build();
            order.addItem(orderItem);

            totalAmount = totalAmount.add(productSnapshotDto.getPrice()
                    .multiply(BigDecimal.valueOf(orderItemCreateDto.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        order.setCurrency(currency);
        return order;
    }

    private Long extractUserId(Jwt jwt) {
        Number idClaim = jwt.getClaim("id");
        return idClaim.longValue();
    }

}
