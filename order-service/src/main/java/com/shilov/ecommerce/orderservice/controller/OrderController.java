package com.shilov.ecommerce.orderservice.controller;

import com.shilov.ecommerce.orderservice.dto.OrderCreateDto;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import com.shilov.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody OrderCreateDto orderCreateDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(jwt, orderCreateDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrderById(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(jwt, id));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponseDto>> getOrders(@AuthenticationPrincipal Jwt jwt,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrders(jwt, pageable));
    }

}
