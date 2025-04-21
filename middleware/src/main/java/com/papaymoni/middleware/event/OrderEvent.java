package com.papaymoni.middleware.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base class for all order events
 * Implements both Serializable (for RabbitMQ) and extends ApplicationEvent (for Spring)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class OrderEvent implements Serializable {
    private String orderId;
    private Long userId;
    private LocalDateTime timestamp = LocalDateTime.now();

    public OrderEvent(String orderId, Long userId) {
        this.orderId = orderId;
        this.userId = userId;
    }
}
