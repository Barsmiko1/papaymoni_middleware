package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.event.PaymentProcessedEvent;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.TransactionService;
import com.papaymoni.middleware.service.UserService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

@Component
public class PaymentEventListener {

    private final TransactionService transactionService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;

    public PaymentEventListener(TransactionService transactionService,
                                UserService userService,
                                NotificationService notificationService,
                                RabbitTemplate rabbitTemplate) {
        this.transactionService = transactionService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = PAYMENT_QUEUE)
    public void handlePaymentEvent(PaymentProcessedEvent event) {
        Transaction transaction = transactionService.getTransactionById(event.getTransactionId());
        User user = userService.getUserById(event.getUserId());

        String title = "Transaction Processed";
        String message = "Your " + transaction.getTransactionType().toLowerCase() +
                " of " + transaction.getAmount() + " " + transaction.getCurrency() +
                " has been processed successfully.";

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
    }
}
