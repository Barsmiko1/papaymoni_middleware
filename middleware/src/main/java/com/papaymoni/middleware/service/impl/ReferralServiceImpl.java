package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.ReferralService;
import com.papaymoni.middleware.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final WalletBalanceService walletBalanceService;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${referral.bonus.ngn.threshold:5000000}")
    private BigDecimal ngnThreshold;

    @Value("${referral.bonus.usd.threshold:5000}")
    private BigDecimal usdThreshold;

    @Value("${referral.bonus.eur.threshold:5000}")
    private BigDecimal eurThreshold;

    @Value("${referral.bonus.gbp.threshold:5000}")
    private BigDecimal gbpThreshold;

    @Value("${referral.bonus.amount:5.00}")
    private BigDecimal bonusAmount;

    @Override
    @Transactional
    public void processReferralBonus(User user, BigDecimal transactionAmount, String currency) {
        log.info("Processing referral bonus check for user: {} with transaction amount: {} {}",
                user.getUsername(), transactionAmount, currency);

        // Check if user was referred
        if (user.getReferredBy() == null || user.getReferredBy().isEmpty()) {
            log.debug("User {} was not referred by anyone", user.getUsername());
            return;
        }

        // Check if user is eligible (not already received bonus)
        if (!isEligibleForReferralBonus(user)) {
            log.debug("User {} is not eligible for referral bonus", user.getUsername());
            return;
        }

        // Get referrer
        Optional<User> referrerOpt = userRepository.findByUsername(user.getReferredBy());
        if (!referrerOpt.isPresent()) {
            log.warn("Referrer {} not found for user {}", user.getReferredBy(), user.getUsername());
            return;
        }

        User referrer = referrerOpt.get();

        // Calculate cumulative transaction amount for the user
        BigDecimal cumulativeAmount = calculateCumulativeTransactionAmount(user, currency);
        BigDecimal threshold = getThresholdForCurrency(currency);

        log.info("User {} cumulative {} transactions: {}, threshold: {}",
                user.getUsername(), currency, cumulativeAmount, threshold);

        // Check if threshold is reached
        if (cumulativeAmount.compareTo(threshold) >= 0) {
            // Check if bonus already processed (using Redis for fast lookup)
            String bonusKey = "referral_bonus_processed:" + user.getId();
            if (redisTemplate.hasKey(bonusKey)) {
                log.debug("Referral bonus already processed for user: {}", user.getUsername());
                return;
            }

            // Process the bonus
            processBonusPayment(referrer, user, currency);

            // Mark as processed (expire after 30 days for cleanup)
            redisTemplate.opsForValue().set(bonusKey, "true", 30, TimeUnit.DAYS);
        }
    }

    @Override
    public boolean isEligibleForReferralBonus(User user) {
        if (user.getReferredBy() == null || user.getReferredBy().isEmpty()) {
            return false;
        }

        // Check Redis cache first for performance
        String bonusKey = "referral_bonus_processed:" + user.getId();
        return !redisTemplate.hasKey(bonusKey);
    }

    @Override
    public BigDecimal getReferralBonusAmount() {
        return bonusAmount;
    }

    private BigDecimal getThresholdForCurrency(String currency) {
        switch (currency.toUpperCase()) {
            case "NGN": return ngnThreshold;
            case "USD": return usdThreshold;
            case "EUR": return eurThreshold;
            case "GBP": return gbpThreshold;
            default:
                log.warn("Unknown currency for referral threshold: {}, using USD threshold", currency);
                return usdThreshold;
        }
    }

    private BigDecimal calculateCumulativeTransactionAmount(User user, String currency) {
        // Get all completed withdrawal and exchange transactions for this currency
        List<Transaction> transactions = transactionRepository.findByUserAndTransactionTypeAndCurrencyAndStatus(
                user, "WITHDRAWAL", currency, "COMPLETED");

        // Add exchange transactions if applicable
        List<Transaction> exchangeTransactions = transactionRepository.findByUserAndTransactionTypeAndCurrencyAndStatus(
                user, "EXCHANGE", currency, "COMPLETED");

        BigDecimal total = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            total = total.add(transaction.getAmount());
        }

        for (Transaction transaction : exchangeTransactions) {
            total = total.add(transaction.getAmount());
        }

        return total;
    }

    private void processBonusPayment(User referrer, User referredUser, String currency) {
        log.info("Processing referral bonus payment: {} USD to referrer {} for referred user {}",
                bonusAmount, referrer.getUsername(), referredUser.getUsername());

        try {
            // Credit USD wallet (bonus is always in USD)
            walletBalanceService.creditWallet(referrer, "USD", bonusAmount);

            // Create transaction record
            Transaction bonusTransaction = new Transaction();
            bonusTransaction.setUser(referrer);
            bonusTransaction.setTransactionType("REFERRAL_BONUS");
            bonusTransaction.setAmount(bonusAmount);
            bonusTransaction.setFee(BigDecimal.ZERO);
            bonusTransaction.setCurrency("USD");
            bonusTransaction.setStatus("COMPLETED");
            bonusTransaction.setExternalReference("REF-" + System.currentTimeMillis());
            bonusTransaction.setPaymentMethod("REFERRAL_SYSTEM");
            bonusTransaction.setPaymentDetails("Referral bonus for " + referredUser.getUsername() +
                    " reaching " + currency + " transaction threshold");
            bonusTransaction.setCreatedAt(LocalDateTime.now());
            bonusTransaction.setCompletedAt(LocalDateTime.now());

            transactionRepository.save(bonusTransaction);

            // Send notifications
            sendReferralBonusNotifications(referrer, referredUser, currency);

            // Publish event for analytics
            publishReferralBonusEvent(referrer, referredUser, currency);

            log.info("Referral bonus processed successfully for referrer: {}", referrer.getUsername());

        } catch (Exception e) {
            log.error("Error processing referral bonus for referrer: {}", referrer.getUsername(), e);
            throw new RuntimeException("Failed to process referral bonus", e);
        }
    }

    private void sendReferralBonusNotifications(User referrer, User referredUser, String currency) {
        // Notify referrer
        String referrerTitle = "Referral Bonus Earned!";
        String referrerMessage = String.format(
                "Congratulations! You've earned $%.2f USD referral bonus because %s (%s) has reached the %s transaction milestone. The bonus has been credited to your USD wallet.",
                bonusAmount,
                referredUser.getFirstName() + " " + referredUser.getLastName(),
                referredUser.getUsername(),
                currency
        );

        notificationService.createNotification(referrer, "EMAIL", referrerTitle, referrerMessage);

        // Notify referred user
        String referredTitle = "Milestone Reached!";
        String referredMessage = String.format(
                "Congratulations! You've reached the %s transaction milestone. Your referrer %s has received a bonus as a thank you for introducing you to Papaymoni!",
                currency,
                referrer.getFirstName() + " " + referrer.getLastName()
        );

        notificationService.createNotification(referredUser, "EMAIL", referredTitle, referredMessage);
    }

    private void publishReferralBonusEvent(User referrer, User referredUser, String currency) {
        // Publish to notification exchange for any additional processing
        NotificationEvent event = new NotificationEvent(
                referrer.getId(),
                "REFERRAL_BONUS",
                "Referral Bonus Processed",
                "Referral bonus processed for " + referredUser.getUsername()
        );

        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY, event);
    }
}
