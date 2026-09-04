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
public class ReservationResponseDto {
    private String reservationId;
    private List<ReservationLineDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationLineDto {
        private String productId;
        private int quantity;
        private ReservationStatus status;

    }
}
