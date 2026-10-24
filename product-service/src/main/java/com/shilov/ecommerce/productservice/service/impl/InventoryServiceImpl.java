package com.shilov.ecommerce.productservice.service.impl;

import com.mongodb.DuplicateKeyException;
import com.shilov.ecommerce.exception.ApplicationException;
import com.shilov.ecommerce.productservice.document.Product;
import com.shilov.ecommerce.productservice.document.StockReservation;
import com.shilov.ecommerce.productservice.dto.ReservationItemDto;
import com.shilov.ecommerce.productservice.dto.ReservationRequestDto;
import com.shilov.ecommerce.productservice.dto.ReservationResponseDto;
import com.shilov.ecommerce.productservice.dto.ReservationResponseDto.ReservationLineDto;
import com.shilov.ecommerce.productservice.enums.ReservationStatus;
import com.shilov.ecommerce.productservice.exception.InventoryException;
import com.shilov.ecommerce.productservice.exception.ProductException;
import com.shilov.ecommerce.productservice.repository.ProductRepository;
import com.shilov.ecommerce.productservice.repository.StockReservationRepository;
import com.shilov.ecommerce.productservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.v;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final MongoTemplate mongoTemplate;
    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;

    @Override
    public ReservationResponseDto reserve(ReservationRequestDto reservationRequestDto) {
        log.debug("Reserve request for order {} ({} item(s))",
                reservationRequestDto.getOrderId(), reservationRequestDto.getItems().size());

        List<StockReservation> existing = stockReservationRepository
                .findByOrderId(reservationRequestDto.getOrderId());
        if (!existing.isEmpty()) {
            log.info("Order {} already has reservations, returning the existing state (idempotent replay)",
                    v("orderId", reservationRequestDto.getOrderId()));
            return buildResponse(reservationRequestDto.getOrderId(), existing);
        }

        List<StockReservation> created = new ArrayList<>();
        try {
            for (ReservationItemDto reservationItemDto : reservationRequestDto.getItems()) {
                Product product = atomicDecrement(reservationItemDto.getProductId(),
                        reservationItemDto.getQuantity());

                StockReservation stockReservation = StockReservation.builder()
                        .orderId(reservationRequestDto.getOrderId())
                        .productId(reservationItemDto.getProductId())
                        .quantity(reservationItemDto.getQuantity())
                        .status(ReservationStatus.RESERVED)
                        .build();

                Optional<ReservationResponseDto> concurrentWinner = trySaveOrDetectRace(
                        reservationRequestDto.getOrderId(), reservationItemDto, stockReservation, created);
                if (concurrentWinner.isPresent()) {
                    return concurrentWinner.get();
                }
            }
        } catch (RuntimeException ex) {
            rollback(created);
            if (ex instanceof ApplicationException) {
                log.info("Reservation for order {} rejected and rolled back ({} line(s) undone): {}",
                        v("orderId", reservationRequestDto.getOrderId()), created.size(), ex.getMessage());
            } else {
                log.warn("Reservation for order {} failed and was rolled back ({} line(s) undone)",
                        v("orderId", reservationRequestDto.getOrderId()), created.size(), ex);
            }
            throw ex;
        }

        log.info("Stock reserved for order {} ({} item(s))",
                v("orderId", reservationRequestDto.getOrderId()), created.size());
        return buildResponse(reservationRequestDto.getOrderId(), created);
    }

    @Override
    public ReservationResponseDto confirm(String orderId) {
        log.debug("Confirm reservations request for order {}", orderId);

        List<StockReservation> reservations = findOrThrow(orderId);
        reservations.forEach(stockReservation -> {
            if (stockReservation.getStatus() == ReservationStatus.RESERVED) {
                stockReservation.setStatus(ReservationStatus.CONFIRMED);
            }
        });

        stockReservationRepository.saveAll(reservations);
        log.info("Reservations of order {} confirmed ({} item(s))", v("orderId", orderId), reservations.size());

        return buildResponse(orderId, reservations);
    }

    @Override
    public ReservationResponseDto release(String orderId) {
        log.debug("Release reservations request for order {}", orderId);

        List<StockReservation> reservations = findOrThrow(orderId);
        for (StockReservation stockReservation : reservations) {
            if (stockReservation.getStatus() == ReservationStatus.RESERVED) {
                atomicIncrement(stockReservation.getProductId(), stockReservation.getQuantity());
                stockReservation.setStatus(ReservationStatus.RELEASED);
            }
        }

        stockReservationRepository.saveAll(reservations);
        log.info("Reservations of order {} released ({} item(s))", v("orderId", orderId), reservations.size());

        return buildResponse(orderId, reservations);
    }

    private Product atomicDecrement(String productId, int quantity) {
        if (!productRepository.existsById(productId)) {
            throw ProductException.notFound();
        }
        Query query = Query.query(Criteria.where("id").is(productId).and("stockQuantity").gte(quantity));
        Update update = new Update().inc("stockQuantity", -quantity);
        Product updatedProduct = mongoTemplate
                .findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Product.class);
        if (updatedProduct == null) {
            throw ProductException.insufficientStock();
        }
        return updatedProduct;
    }

    private void atomicIncrement(String productId, int quantity) {
        Query query = Query.query(Criteria.where("id").is(productId));
        Update update = new Update().inc("stockQuantity", quantity);
        mongoTemplate.findAndModify(query, update, Product.class);
    }

    private Optional<ReservationResponseDto> trySaveOrDetectRace(
            String orderId, ReservationItemDto reservationItemDto,
            StockReservation stockReservation, List<StockReservation> created) {
        try {
            created.add(stockReservationRepository.save(stockReservation));
            return Optional.empty();
        } catch (DuplicateKeyException ex) {
            atomicIncrement(reservationItemDto.getProductId(), reservationItemDto.getQuantity());
            rollback(created);
            log.warn("Concurrent duplicate reserve for order {} product {} - "
                    + "another request already won the race, returning its result",
                    v("orderId", orderId), v("productId", reservationItemDto.getProductId()));
            List<StockReservation> winner = stockReservationRepository.findByOrderId(orderId);
            return Optional.of(buildResponse(orderId, winner));
        }
    }

    private void rollback(List<StockReservation> created) {
        for (StockReservation stockReservation : created) {
            atomicIncrement(stockReservation.getProductId(), stockReservation.getQuantity());
        }
        stockReservationRepository.deleteAll(created);
    }

    private List<StockReservation> findOrThrow(String orderId) {
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw InventoryException.reservationNotFound();
        }
        return reservations;
    }

    private ReservationResponseDto buildResponse(String orderId, List<StockReservation> reservations) {
        List<ReservationLineDto> reservationLines = reservations.stream()
                .map(reservationLineDto -> ReservationLineDto.builder()
                        .productId(reservationLineDto.getProductId())
                        .quantity(reservationLineDto.getQuantity())
                        .status(reservationLineDto.getStatus())
                        .build())
                .toList();
        return ReservationResponseDto.builder().reservationId(orderId).items(reservationLines).build();
    }

}
