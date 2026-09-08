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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public Order process(Order order) {
        log.info("Saga started for order {} (total: {} {})",
                order.getId(), order.getTotalAmount(), order.getCurrency());

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
            order.setStatus(OrderStatus.STOCK_RESERVED);
            log.info("Stock reserved for order {}", order.getId());
            return orderRepository.save(order);
        } catch (SagaStepException ex) {
            log.warn("Stock reservation failed for order {}: {}", order.getId(), ex.getMessage());
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason("Stock reservation failed: " + ex.getMessage());
            return outboxService.saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
        }
    }

    private Order chargePayment(Order order) {
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CONFIRMED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            return order;
        }

        log.info("Charging payment for order {}", order.getId());
        PaymentResponseDto paymentResponseDto;
        try {
            paymentResponseDto = paymentServiceClient
                    .charge(order.getId(), order.getTotalAmount(), order.getCurrency());
        } catch (SagaStepException ex) {
            log.error("Payment service call failed for order {}: {}", order.getId(), ex.getMessage());
            log.info("Compensating: releasing stock for order {}", order.getId());
            productServiceClient.release(order.getId().toString());
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason("Payment service unavailable");
            return outboxService.saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
        }

        if (paymentResponseDto.getStatus() == PaymentStatus.COMPLETED) {
            order.setStatus(OrderStatus.PAID);
            log.info("Payment completed for order {}", order.getId());
            return orderRepository.save(order);
        }

        log.warn("Payment declined for order {}: {}", order.getId(), paymentResponseDto.getFailureReason());
        log.info("Compensating: releasing stock for order {}", order.getId());
        productServiceClient.release(order.getId().toString());
        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason("Payment declined: " + paymentResponseDto.getFailureReason());
        return outboxService.saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
    }

    private Order confirmStock(Order order) {
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return order;
        }
        try {
            productServiceClient.confirm(order.getId().toString());
            order.setStatus(OrderStatus.CONFIRMED);
            log.info("Stock confirmed for order {}", order.getId());
        } catch (SagaStepException ex) {
            log.error("Stock confirmation failed for order {} after successful payment - compensating: {}",
                    order.getId(), ex.getMessage());
            compensateAfterConfirmationFailure(order, ex);
        }

        OutboxEventType outboxEventType = order.getStatus() == OrderStatus.CONFIRMED
                ? OutboxEventType.ORDER_CONFIRMED
                : OutboxEventType.ORDER_CANCELLED;
        return outboxService.saveAndPublish(order, outboxEventType);
    }

    private void compensateAfterConfirmationFailure(Order order, SagaStepException originalEx) {
        try {
            paymentServiceClient.refund(order.getId());
            productServiceClient.release(order.getId().toString());
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason("Payment refunded after stock confirmation failure: " + originalEx.getMessage());
        } catch (SagaStepException compensationEx) {
            log.error("Compensation failed for order {} - refund/release did not complete. Manual intervention required.",
                    order.getId(), compensationEx);

            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason("Automatic compensation failed - requires manual review: "
                    + compensationEx.getMessage());
        }
    }
}
