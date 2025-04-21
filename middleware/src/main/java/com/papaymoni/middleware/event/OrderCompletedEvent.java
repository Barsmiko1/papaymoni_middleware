package com.papaymoni.middleware.event;

import lombok.NoArgsConstructor;

/**
 * Event fired when an order is completed
 */
@NoArgsConstructor
public class OrderCompletedEvent extends OrderEvent {
    public OrderCompletedEvent(String orderId, Long userId) {
        super(orderId, userId);
    }
}
