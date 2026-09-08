package com.shilov.ecommerce.productservice.dto;

import com.shilov.ecommerce.productservice.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class ReservationResponseDto {
    private String reservationId;
    private List<ReservationLineDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class ReservationLineDto {
        private String productId;
        private Integer quantity;
        private ReservationStatus status;

    }
}
