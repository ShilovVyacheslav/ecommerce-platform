package com.shilov.ecommerce.productservice.repository;

import com.shilov.ecommerce.productservice.document.StockReservation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockReservationRepository extends MongoRepository<StockReservation, String> {

    List<StockReservation> findByOrderId(String orderId);

}
