package com.shilov.ecommerce.notificationservice.service.impl;

import com.shilov.ecommerce.events.OrderEventPayload;
import com.shilov.ecommerce.notificationservice.service.OrderNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.v;

@Slf4j
@Service
public class OrderNotificationServiceImpl implements OrderNotificationService {

    public void handle(OrderEventPayload event) {
        switch (event.getStatus()) {
            case "CONFIRMED" -> log.info("[EMAIL SIMULATED] Order {} confirmed - confirmation sent to user {}",
                    v("orderId", event.getOrderId()), v("targetUserId", event.getUserId()));
            case "CANCELLED" -> log.info("[EMAIL SIMULATED] Order {} cancelled - cancellation notice sent to user {}",
                    v("orderId", event.getOrderId()), v("targetUserId", event.getUserId()));
            default -> log.warn("Order event {} has an unknown status {}, notification skipped",
                    v("orderId", event.getOrderId()), v("orderStatus", event.getStatus()));
        }
    }

}
