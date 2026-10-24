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
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.shilov.ecommerce.orderservice.enums.ErrorCode.INSUFFICIENT_STOCK_ERROR;
import static com.shilov.ecommerce.orderservice.enums.ErrorCode.PRODUCT_NOT_FOUND_ERROR;
import static net.logstash.logback.argument.StructuredArguments.v;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;
    private static final String MANUAL_REVIEW_PREFIX = "Automatic compensation failed - requires manual review: ";
    private static final String ORDER_ID_MDC_KEY = "orderId";

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public Order process(Order order) {
        MDC.put(ORDER_ID_MDC_KEY, String.valueOf(order.getId()));
        try {
            OrderStatus initialStatus = order.getStatus();
            if (isTerminal(initialStatus)) {
                log.info("Saga skipped for order {}: already {}", order.getId(), initialStatus);
                return order;
            }

            log.info("Saga started for order {} from {} ({} {})",
                    order.getId(), initialStatus, order.getTotalAmount(), order.getCurrency());
            long startNanos = System.nanoTime();

            order = runSteps(order);

            logOutcome(order, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            return order;
        } finally {
            MDC.remove(ORDER_ID_MDC_KEY);
        }
    }

    private Order runSteps(Order order) {
        order = reserveStock(order);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        order = chargePayment(order);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        return confirmStock(order);
    }

    private void logOutcome(Order order, long durationMs) {
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Saga completed for order {}: {} in {} ms",
                    order.getId(), v("orderStatus", order.getStatus()), v("durationMs", durationMs));
        } else if (order.getFailureReason() != null && order.getFailureReason().startsWith(MANUAL_REVIEW_PREFIX)) {
            log.error("Saga {} for order {} in {} ms and REQUIRES MANUAL REVIEW: {}",
                    v("orderStatus", order.getStatus()), order.getId(), v("durationMs", durationMs),
                    order.getFailureReason());
        } else {
            log.warn("Saga {} for order {} in {} ms: {}",
                    v("orderStatus", order.getStatus()), order.getId(), v("durationMs", durationMs),
                    order.getFailureReason());
        }
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

        log.debug("Reserving stock for order {} ({} item(s))", order.getId(), items.size());
        try {
            productServiceClient.reserve(order.getId().toString(), items);
        } catch (SagaStepException ex) {
            String reason = "Stock reservation failed: " + ex.getMessage();
            if (isBusinessRejection(ex)) {
                log.debug("Stock reservation rejected for order {}: {}", order.getId(), ex.getMessage());
                return cancel(order, reason, true);
            }
            log.warn("Stock reservation outcome unknown for order {} - releasing defensively: {}",
                    order.getId(), causeOf(ex));
            return cancel(order, reason, releaseStockQuietly(order));
        }

        order.setStatus(OrderStatus.STOCK_RESERVED);
        log.debug("Stock reserved for order {}", order.getId());
        return orderRepository.save(order);
    }

    private Order chargePayment(Order order) {
        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            return order;
        }

        log.debug("Charging payment for order {}", order.getId());
        PaymentResponseDto payment;
        try {
            payment = paymentServiceClient.charge(order.getId(), order.getTotalAmount(), order.getCurrency());
        } catch (SagaStepException ex) {
            log.warn("Payment call failed for order {} - outcome unknown, refunding defensively: {}",
                    order.getId(), causeOf(ex));
            return compensateUnknownChargeOutcome(order, "Payment service unavailable");
        }

        if (payment == null || payment.getStatus() == null) {
            log.warn("Payment service returned no payment status for order {} - outcome unknown, refunding defensively",
                    order.getId());
            return compensateUnknownChargeOutcome(order, "Payment service returned an invalid response");
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            order.setStatus(OrderStatus.PAID);
            log.debug("Payment completed for order {}", order.getId());
            return orderRepository.save(order);
        }

        String declineReason = Objects.requireNonNullElse(payment.getFailureReason(), "unknown");
        log.debug("Payment declined for order {}: {}", order.getId(), declineReason);
        return cancel(order, "Payment declined: " + declineReason, releaseStockQuietly(order));
    }

    private Order confirmStock(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            return order;
        }
        try {
            productServiceClient.confirm(order.getId().toString());
        } catch (SagaStepException ex) {
            log.warn("Stock confirmation failed for order {} after successful payment - refunding and releasing: {}",
                    order.getId(), causeOf(ex));

            boolean refunded = refundQuietly(order);
            boolean released = releaseStockQuietly(order);
            boolean compensated = refunded && released;

            String reason = compensated
                    ? "Payment refunded after stock confirmation failure: " + ex.getMessage()
                    : "Stock confirmation failed: " + ex.getMessage();

            return cancel(order, reason, compensated);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        log.debug("Stock confirmed for order {}", order.getId());
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
            log.debug("Compensating: releasing stock for order {}", order.getId());
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
            log.debug("Compensating: refunding payment for order {}", order.getId());
            paymentServiceClient.refund(order.getId());
            return true;
        } catch (RuntimeException ex) {
            log.error("COMPENSATION FAILED: refund for order {} - manual intervention required",
                    order.getId(), ex);
            return false;
        }
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.CONFIRMED || status == OrderStatus.CANCELLED;
    }

    private static String causeOf(SagaStepException ex) {
        Throwable cause = ex.getCause();
        return cause == null ? ex.getMessage() : ex.getMessage() + " (" + cause + ")";
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
