package com.shilov.ecommerce.notificationservice.service;

import com.shilov.ecommerce.events.OrderEventPayload;

public interface OrderNotificationService {

    void handle(OrderEventPayload event);

}
