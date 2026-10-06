package com.shilov.ecommerce.orderservice.saga;

import com.shilov.ecommerce.enums.payment.PaymentStatus;
import com.shilov.ecommerce.orderservice.client.PaymentServiceClient;
import com.shilov.ecommerce.orderservice.client.ProductServiceClient;
import com.shilov.ecommerce.orderservice.dto.client.PaymentResponseDto;
import com.shilov.ecommerce.orderservice.dto.client.ReservationItemRequestDto;
import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import com.shilov.ecommerce.orderservice.enums.OutboxEventType;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import com.shilov.ecommerce.orderservice.repository.OrderRepository;
import com.shilov.ecommerce.orderservice.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.shilov.ecommerce.orderservice.enums.ErrorCode.INSUFFICIENT_STOCK_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.PRODUCT_NOT_FOUND_ERROR;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;
    private static final String MANUAL_REVIEW_PREFIX = "Automatic compensation failed - requires manual review: ";

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public Order process(Order order) {
        log.info("Saga started for order {} (status: {}, total: {} {})",
                order.getId(), order.getStatus(), order.getTotalAmount(), order.getCurrency());

        order = reserveStock(order);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Saga terminated for order {} at STOCK_RESERVED step: {}",
                    order.getId(), order.getFailureReason());
            return order;
        }
        order = chargePayment(order);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Saga terminated for order {} at PAID step: {}",
                    order.getId(), order.getFailureReason());
            return order;
        }

        order = confirmStock(order);
        log.info("Saga finished for order {} with status {}", order.getId(), order.getStatus());
        return order;
    }

    private Order reserveStock(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            return order;
        }

        List<ReservationItemRequestDto> items = order.getItems().stream()
                .map(item -> ReservationItemRequestDto.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        log.info("Reserving stock for order {} ({} item(s))", order.getId(), items.size());
        try {
            productServiceClient.reserve(order.getId().toString(), items);
        } catch (SagaStepException ex) {
            log.warn("Stock reservation failed for order {}: {}", order.getId(), ex.getMessage());
            String reason = "Stock reservation failed: " + ex.getMessage();
            if (isBusinessRejection(ex)) {
                return cancel(order, reason, true);
            }
            return cancel(order, reason, releaseStockQuietly(order));
        }

        order.setStatus(OrderStatus.STOCK_RESERVED);
        log.info("Stock reserved for order {}", order.getId());
        return orderRepository.save(order);
    }

    private Order chargePayment(Order order) {
        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            return order;
        }

        log.info("Charging payment for order {}", order.getId());
        PaymentResponseDto payment;
        try {
            payment = paymentServiceClient.charge(order.getId(), order.getTotalAmount(), order.getCurrency());
        } catch (SagaStepException ex) {
            log.error("Payment call failed for order {} - outcome unknown: {}", order.getId(), ex.getMessage());
            return compensateUnknownChargeOutcome(order, "Payment service unavailable");
        }

        if (payment == null || payment.getStatus() == null) {
            log.error("Payment service returned no payment status for order {} - outcome unknown", order.getId());
            return compensateUnknownChargeOutcome(order, "Payment service returned an invalid response");
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            order.setStatus(OrderStatus.PAID);
            log.info("Payment completed for order {}", order.getId());
            return orderRepository.save(order);
        }

        String declineReason = Objects.requireNonNullElse(payment.getFailureReason(), "unknown");
        log.warn("Payment declined for order {}: {}", order.getId(), declineReason);
        return cancel(order, "Payment declined: " + declineReason, releaseStockQuietly(order));
    }

    private Order confirmStock(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            return order;
        }
        try {
            productServiceClient.confirm(order.getId().toString());
        } catch (SagaStepException ex) {
            log.error("Stock confirmation failed for order {} after successful payment - compensating: {}",
                    order.getId(), ex.getMessage());

            boolean refunded = refundQuietly(order);
            boolean released = releaseStockQuietly(order);
            boolean compensated = refunded && released;

            String reason = compensated
                    ? "Payment refunded after stock confirmation failure: " + ex.getMessage()
                    : "Stock confirmation failed: " + ex.getMessage();

            return cancel(order, reason, compensated);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        log.info("Stock confirmed for order {}", order.getId());
        return outboxService.saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED);
    }

    private Order cancel(Order order, String reason, boolean compensationSucceeded) {
        String fullReason = compensationSucceeded ? reason : MANUAL_REVIEW_PREFIX + reason;
        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason(truncate(fullReason));
        return outboxService.saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
    }

    private boolean releaseStockQuietly(Order order) {
        try {
            log.info("Compensating: releasing stock for order {}", order.getId());
            productServiceClient.release(order.getId().toString());
            return true;
        } catch (RuntimeException ex) {
            log.error("COMPENSATION FAILED: stock release for order {} - manual intervention required",
                    order.getId(), ex);
            return false;
        }
    }

    private Order compensateUnknownChargeOutcome(Order order, String reason) {
        boolean refunded = refundQuietly(order);
        boolean released = releaseStockQuietly(order);
        return cancel(order, reason, refunded && released);
    }

    private boolean refundQuietly(Order order) {
        try {
            log.info("Compensating: refunding payment for order {}", order.getId());
            paymentServiceClient.refund(order.getId());
            return true;
        } catch (RuntimeException ex) {
            log.error("COMPENSATION FAILED: refund for order {} - manual intervention required",
                    order.getId(), ex);
            return false;
        }
    }

    private static boolean isBusinessRejection(SagaStepException ex) {
        return ex.getCode() == INSUFFICIENT_STOCK_ERROR.getCode() ||
               ex.getCode() == PRODUCT_NOT_FOUND_ERROR.getCode();
    }

    private static String truncate(String reason) {
        if (reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LENGTH - 3) + "...";
    }
}
