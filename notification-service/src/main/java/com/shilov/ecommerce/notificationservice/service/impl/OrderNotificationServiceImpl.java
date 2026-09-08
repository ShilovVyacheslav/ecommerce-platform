package com.shilov.ecommerce.notificationservice.service.impl;

import com.shilov.ecommerce.events.OrderEventPayload;
import com.shilov.ecommerce.notificationservice.service.OrderNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderNotificationServiceImpl implements OrderNotificationService {

    public void handle(OrderEventPayload event) {
        switch (event.getStatus()) {
            case "CONFIRMED" -> log.info("[EMAIL SIMULATED] Order {} confirmed - sending confirmation to user {}",
                    event.getOrderId(), event.getUserId());
            case "CANCELLED" -> log.info("[EMAIL SIMULATED] Order {} cancelled ({}) - notifying user {}",
                    event.getOrderId(), event.getFailureReason(), event.getUserId());
            default -> log.warn("Unknown order status in event: {}", event.getStatus());
        }
    }

}
