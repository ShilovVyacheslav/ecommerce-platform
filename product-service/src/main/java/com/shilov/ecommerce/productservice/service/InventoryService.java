package com.shilov.ecommerce.productservice.service;

import com.shilov.ecommerce.productservice.dto.ReservationRequestDto;
import com.shilov.ecommerce.productservice.dto.ReservationResponseDto;

public interface InventoryService {

    ReservationResponseDto reserve(ReservationRequestDto reservationRequestDto);

    ReservationResponseDto confirm(String orderId);

    ReservationResponseDto release(String orderId);

}
