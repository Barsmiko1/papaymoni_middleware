package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.CashbackService;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashbackServiceImpl implements CashbackService {

    private final WalletBalanceService walletBalanceService;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${cashback.rate:0.0001}")
    private BigDecimal cashbackRate; // 0.01% = 0.0001

    @Value("${cashback.min.amount:0.01}")
    private BigDecimal minCashbackAmount;

    @Value("${cashback.enabled:true}")
    private boolean cashbackEnabled;

    // Eligible transaction types for cashback
    private static final List<String> ELIGIBLE_TRANSACTION_TYPES = Arrays.asList(
            "WITHDRAWAL", "EXCHANGE", "INTERNAL_TRANSFER");

    @Override
    @Transactional
    public void processCashback(User user, BigDecimal transactionAmount, String currency, String transactionType) {
        if (!cashbackEnabled) {
            log.debug("Cashback is disabled");
            return;
        }

        // Check if transaction type is eligible for cashback
        if (!ELIGIBLE_TRANSACTION_TYPES.contains(transactionType)) {
            log.debug("Transaction type {} is not eligible for cashback", transactionType);
            return;
        }

        log.info("Processing cashback for user: {} on {} {} transaction",
                user.getUsername(), transactionAmount, currency);

        try {
            // Calculate cashback amount
            BigDecimal cashbackAmount = calculateCashbackAmount(transactionAmount);

            // Check minimum cashback threshold
            if (cashbackAmount.compareTo(minCashbackAmount) < 0) {
                log.debug("Cashback amount {} is below minimum threshold {}",
                        cashbackAmount, minCashbackAmount);
                return;
            }

            // Credit cashback to user's wallet in the same currency
            walletBalanceService.creditWallet(user, currency, cashbackAmount);

            // Create cashback transaction record
            Transaction cashbackTransaction = createCashbackTransaction(user, cashbackAmount, currency, transactionType);
            transactionRepository.save(cashbackTransaction);

            // Send notification if cashback is significant (≥ equivalent of $0.01)
            if (shouldNotifyForCashback(cashbackAmount, currency)) {
                sendCashbackNotification(user, cashbackAmount, currency, transactionType);
            }

            // Publish cashback event for analytics
            publishCashbackEvent(user, cashbackAmount, currency, transactionType);

            log.info("Cashback processed successfully: {} {} for user {}",
                    cashbackAmount, currency, user.getUsername());

        } catch (Exception e) {
            log.error("Error processing cashback for user: {}", user.getUsername(), e);
            // Don't throw exception - cashback failure shouldn't fail the main transaction
        }
    }

    @Override
    public BigDecimal calculateCashbackAmount(BigDecimal transactionAmount) {
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate 0.01% of transaction amount
        BigDecimal cashback = transactionAmount.multiply(cashbackRate);

        // Round to 2 decimal places
        return cashback.setScale(2, RoundingMode.HALF_UP);
    }

    private Transaction createCashbackTransaction(User user, BigDecimal cashbackAmount,
                                                  String currency, String originalTransactionType) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setTransactionType("CASHBACK");
        transaction.setAmount(cashbackAmount);
        transaction.setFee(BigDecimal.ZERO);
        transaction.setCurrency(currency);
        transaction.setStatus("COMPLETED");
        transaction.setExternalReference("CASHBACK-" + System.currentTimeMillis());
        transaction.setPaymentMethod("CASHBACK_SYSTEM");
        transaction.setPaymentDetails("Cashback for " + originalTransactionType + " transaction");
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCompletedAt(LocalDateTime.now());

        return transaction;
    }

    private boolean shouldNotifyForCashback(BigDecimal cashbackAmount, String currency) {
        // Notify for cashback equivalent to $0.10 or more
        BigDecimal notificationThreshold = getNotificationThresholdForCurrency(currency);
        return cashbackAmount.compareTo(notificationThreshold) >= 0;
    }

    private BigDecimal getNotificationThresholdForCurrency(String currency) {
        switch (currency.toUpperCase()) {
            case "NGN": return new BigDecimal("15.00"); // ~$0.01
            case "USD": return new BigDecimal("0.01");
            case "EUR": return new BigDecimal("0.009");
            case "GBP": return new BigDecimal("0.008");
            case "USDT": return new BigDecimal("0.01");
            default: return new BigDecimal("0.01");
        }
    }

    private void sendCashbackNotification(User user, BigDecimal cashbackAmount,
                                          String currency, String transactionType) {
        String title = "Cashback Earned!";
        String message = String.format(
                "You've earned %s %s cashback on your recent %s transaction. " +
                        "The cashback has been credited to your %s wallet.",
                cashbackAmount, currency, transactionType.toLowerCase(), currency
        );

        notificationService.createNotification(user, "EMAIL", title, message);
    }

    private void publishCashbackEvent(User user, BigDecimal cashbackAmount,
                                      String currency, String transactionType) {
        NotificationEvent event = new NotificationEvent(
                user.getId(),
                "CASHBACK",
                "Cashback Processed",
                String.format("Cashback of %s %s processed for %s transaction",
                        cashbackAmount, currency, transactionType)
        );

        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY, event);
    }
}

