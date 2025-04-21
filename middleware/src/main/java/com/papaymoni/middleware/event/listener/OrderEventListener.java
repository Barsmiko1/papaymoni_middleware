package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.event.*;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.OrderService;
import com.papaymoni.middleware.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.function.BiFunction;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

/**
 * Listener for order events
 * Implements both Spring's event system and RabbitMQ listeners
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Listen for RabbitMQ order events
     */
    @RabbitListener(queues = ORDER_QUEUE)
    public void handleRabbitMQOrderEvent(Object event) {
        log.info("Received RabbitMQ event: {}", event.getClass().getSimpleName());

        if (event instanceof OrderCreatedEvent) {
            handleOrderCreatedEvent((OrderCreatedEvent) event);
        } else if (event instanceof OrderPaidEvent) {
            handleOrderPaidEvent((OrderPaidEvent) event);
        } else if (event instanceof OrderCompletedEvent) {
            handleOrderCompletedEvent((OrderCompletedEvent) event);
        } else {
            log.warn("Unknown event type: {}", event.getClass().getName());
        }
    }

    /**
     * Listen for Spring application events - Order Created
     */
    @Async
    @EventListener
    public void handleSpringOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Received Spring event: OrderCreatedEvent for order {}", event.getOrderId());
        handleOrderCreatedEvent(event);
    }

    /**
     * Listen for Spring application events - Order Paid
     * Only process after transaction is committed
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSpringOrderPaidEvent(OrderPaidEvent event) {
        log.info("Received Spring event: OrderPaidEvent for order {}", event.getOrderId());
        handleOrderPaidEvent(event);
    }

    /**
     * Listen for Spring application events - Order Completed
     * Only process after transaction is committed
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSpringOrderCompletedEvent(OrderCompletedEvent event) {
        log.info("Received Spring event: OrderCompletedEvent for order {}", event.getOrderId());
        handleOrderCompletedEvent(event);
    }

    /**
     * Handle order created event
     */
    private void handleOrderCreatedEvent(OrderCreatedEvent event) {
        processOrderEvent(event, "Order Created",
                (order, user) -> "Your order " + order.getId() + " has been created successfully.");
    }

    /**
     * Handle order paid event
     */
    private void handleOrderPaidEvent(OrderPaidEvent event) {
        processOrderEvent(event, "Order Paid",
                (order, user) -> "Your payment for order " + order.getId() + " has been confirmed.");
    }

    /**
     * Handle order completed event
     */
    private void handleOrderCompletedEvent(OrderCompletedEvent event) {
        processOrderEvent(event, "Order Completed",
                (order, user) -> "Your order " + order.getId() + " has been completed successfully. Assets have been released.");
    }

    /**
     * Process an order event with the given title and message function
     */
    private void processOrderEvent(OrderEvent event, String title, BiFunction<Order, User, String> messageFunction) {
        try {
            Order order = orderService.getOrderById(event.getOrderId());
            User user = userService.getUserById(event.getUserId());

            String message = messageFunction.apply(order, user);

            // Send notification to user
            notificationService.createNotification(
                    user,
                    "EMAIL",
                    title,
                    message
            );

            // Publish notification event
            rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY,
                    new NotificationEvent(user.getId(), "EMAIL", title, message));

            log.info("Processed {} for order {}", event.getClass().getSimpleName(), event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing event {} for order {}: {}",
                    event.getClass().getSimpleName(), event.getOrderId(), e.getMessage(), e);
        }
    }
}
