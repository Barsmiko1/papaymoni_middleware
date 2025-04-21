package com.papaymoni.middleware.event;

import lombok.NoArgsConstructor;

/**
 * Event fired when an order is paid
 */
@NoArgsConstructor
public class OrderPaidEvent extends OrderEvent {
    public OrderPaidEvent(String orderId, Long userId) {
        super(orderId, userId);
    }
}
