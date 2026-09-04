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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

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

        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = null;

        for (OrderItemCreateDto itemCreateDto : orderCreateDto.getItems()) {
            ProductSnapshotDto productSnapshotDto =
                    productServiceClient.getProduct(itemCreateDto.getProductId());
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
                    .quantity(itemCreateDto.getQuantity())
                    .unitPrice(productSnapshotDto.getPrice())
                    .build();
            order.addItem(orderItem);

            totalAmount = totalAmount.add(productSnapshotDto.getPrice()
                    .multiply(BigDecimal.valueOf(itemCreateDto.getQuantity())));
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
