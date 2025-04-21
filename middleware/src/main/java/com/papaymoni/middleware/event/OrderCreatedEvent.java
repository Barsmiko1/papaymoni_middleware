package com.papaymoni.middleware.event;

import lombok.NoArgsConstructor;

/**
 * Event fired when an order is created
 */
@NoArgsConstructor
public class OrderCreatedEvent extends OrderEvent {
    public OrderCreatedEvent(String orderId, Long userId) {
        super(orderId, userId);
    }
}
