package com.shilov.ecommerce.productservice.controller;

import com.shilov.ecommerce.productservice.dto.ReservationRequestDto;
import com.shilov.ecommerce.productservice.dto.ReservationResponseDto;
import com.shilov.ecommerce.productservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponseDto> reserve(
            @Valid @RequestBody ReservationRequestDto reservationRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.reserve(reservationRequestDto));
    }

    @PostMapping("/reservations/{orderId}/confirm")
    public ResponseEntity<ReservationResponseDto> confirm(@PathVariable String orderId) {
        return ResponseEntity.ok(inventoryService.confirm(orderId));
    }

    @PostMapping("/reservations/{orderId}/release")
    public ResponseEntity<ReservationResponseDto> release(@PathVariable String orderId) {
        return ResponseEntity.ok(inventoryService.release(orderId));
    }
}
