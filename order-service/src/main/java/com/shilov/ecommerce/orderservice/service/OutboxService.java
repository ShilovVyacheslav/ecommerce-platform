package com.shilov.ecommerce.orderservice.service;

import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.enums.OutboxEventType;

public interface OutboxService {

    Order saveAndPublish(Order order, OutboxEventType outboxEventType);

}
