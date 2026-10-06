package com.shilov.ecommerce.orderservice.support;

import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.entity.OrderItem;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public final class TestOrders {

    public static final long USER_ID = 1L;
    public static final String CURRENCY = "USD";

    public static final String KEYBOARD_ID = UUID.randomUUID().toString();
    public static final String KEYBOARD_NAME = "Gaming Keyboard";
    public static final int KEYBOARD_QUANTITY = 2;

    public static final String MOUSE_ID = UUID.randomUUID().toString();
    public static final String MOUSE_NAME = "Wireless Mouse";
    public static final int MOUSE_QUANTITY = 1;

    public static final String HUB_ID = UUID.randomUUID().toString();
    public static final String HUB_NAME = "USB-C Hub";
    public static final int HUB_QUANTITY = 3;

    public static final BigDecimal KEYBOARD_UNIT_PRICE = new BigDecimal("89.99");
    public static final BigDecimal MOUSE_UNIT_PRICE = new BigDecimal("29.99");
    public static final BigDecimal HUB_UNIT_PRICE = new BigDecimal("45.50");
    public static final BigDecimal TOTAL = new BigDecimal("346.47");

    private TestOrders() {}

    public static Order created() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .status(OrderStatus.CREATED)
                .totalAmount(TOTAL)
                .currency(CURRENCY)
                .build();
        order.addItem(item(KEYBOARD_ID, KEYBOARD_NAME, KEYBOARD_QUANTITY, KEYBOARD_UNIT_PRICE));
        order.addItem(item(MOUSE_ID, MOUSE_NAME, MOUSE_QUANTITY, MOUSE_UNIT_PRICE));
        order.addItem(item(HUB_ID, HUB_NAME, HUB_QUANTITY, HUB_UNIT_PRICE));
        return order;
    }

    private static OrderItem item(String productId, String productName,
                                  int productQuantity, BigDecimal productPrice) {
        return OrderItem.builder()
                .productId(productId)
                .productName(productName)
                .quantity(productQuantity)
                .unitPrice(productPrice)
                .build();
    }

}
