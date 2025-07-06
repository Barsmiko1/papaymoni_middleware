package com.papaymoni.middleware.event.listener;

import com.papaymoni.middleware.dto.TransactionCacheDto;
import com.papaymoni.middleware.dto.UserEventDto;
import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.event.PaymentProcessedEvent;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.TransactionService;
import com.papaymoni.middleware.service.UserService;
import com.papaymoni.middleware.service.impl.TransactionServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final TransactionServiceImpl transactionService;;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ConversionService conversionService;

    @RabbitListener(queues = PAYMENT_QUEUE)
    public void handlePaymentEvent(PaymentProcessedEvent event) {
        log.info("Handling payment event for transaction: {}", event.getTransactionId());

        try {
            // Use the DTO method instead
            TransactionCacheDto transactionDto = transactionService.getTransactionDtoById(event.getTransactionId());

            // Use enhanced notification methods with DTO data
            if ("DEPOSIT".equals(transactionDto.getTransactionType())) {
                // Create temporary user object for notification
                User tempUser = new User();
                tempUser.setId(transactionDto.getUserId());
                tempUser.setFirstName(transactionDto.getUserFirstName());
                tempUser.setEmail(transactionDto.getUserEmail());

                notificationService.sendDepositNotification(
                        tempUser,
                        transactionDto.getAmount(),
                        transactionDto.getCurrency(),
                        transactionDto.getId()
                );
            } else if ("WITHDRAWAL".equals(transactionDto.getTransactionType())) {
                // Create temporary user object for notification
                User tempUser = new User();
                tempUser.setId(transactionDto.getUserId());
                tempUser.setFirstName(transactionDto.getUserFirstName());
                tempUser.setEmail(transactionDto.getUserEmail());

                notificationService.sendWithdrawalNotification(
                        tempUser,
                        transactionDto.getAmount(),
                        transactionDto.getCurrency(),
                        transactionDto.getId(),
                        transactionDto.getFee()
                );
            }

            log.info("Successfully processed payment event for transaction: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Error handling payment event: {}", e.getMessage(), e);
        }
    }

}