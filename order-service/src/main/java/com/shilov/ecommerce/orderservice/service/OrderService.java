package com.shilov.ecommerce.orderservice.service;

import com.shilov.ecommerce.orderservice.dto.OrderCreateDto;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface OrderService {

    OrderResponseDto createOrder(Jwt jwt, OrderCreateDto orderCreateDto);

    OrderResponseDto getOrderById(Jwt jwt, UUID orderId);

    Page<OrderResponseDto> getOrders(Jwt jwt, Pageable pageable);

}
