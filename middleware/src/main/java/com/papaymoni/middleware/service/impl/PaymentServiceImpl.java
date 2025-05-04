//package com.papaymoni.middleware.service.impl;
//
//import com.papaymoni.middleware.event.PaymentProcessedEvent;
//import com.papaymoni.middleware.exception.InsufficientBalanceException;
//import com.papaymoni.middleware.model.Order;
//import com.papaymoni.middleware.model.Transaction;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.model.VirtualAccount;
//import com.papaymoni.middleware.repository.TransactionRepository;
//import com.papaymoni.middleware.service.GLService;
//import com.papaymoni.middleware.service.PaymentService;
//import com.papaymoni.middleware.service.PalmpayPaymentGatewayService;
//import com.papaymoni.middleware.service.WalletBalanceService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.Map;
//
//import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_EXCHANGE;
//import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_PROCESSED_KEY;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class PaymentServiceImpl implements PaymentService {
//    private final TransactionRepository transactionRepository;
//    private final GLService glService;
//    private final PalmpayPaymentGatewayService palmpayPaymentGatewayService;
//    private final RabbitTemplate rabbitTemplate;
//
//    // Fee is 1.2% capped at 1,200 NGN
//    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.012");
//    private static final BigDecimal FEE_CAP = new BigDecimal("1200.00");
//    private static final String FEE_CURRENCY = "NGN";
//
//    // wallet service
//    private final WalletBalanceService walletBalanceService;
//
//    /**
//     * Calculate the fee for a given amount
//     * Fee is 1.2% capped at 1,200 NGN
//     * @param amount the amount to calculate fee for
//     * @return the calculated fee
//     */
//    @Override
//    public BigDecimal calculateFee(BigDecimal amount) {
//        // Calculate fee as 1.2% of the amount
//        BigDecimal fee = amount.multiply(FEE_PERCENTAGE);
//
//        // If the fee currency is not NGN, we would need to convert it
//        // For now, we'll assume the fee is already in NGN
//        BigDecimal feeInNGN = fee;
//
//        // Apply the cap if the fee exceeds 1,200 NGN
//        if (feeInNGN.compareTo(FEE_CAP) > 0) {
//            fee = FEE_CAP;
//        }
//
//        log.info("Calculated fee for amount {}: {}", amount, fee);
//        return fee;
//    }
//
//    /**
//     * Check if a user has sufficient balance for a transaction
//     * @param user the user to check
//     * @param amount the amount to check
//     * @return true if the user has sufficient balance
//     */
//    @Override
//    public boolean hasSufficientBalance(User user, BigDecimal amount) {
//        String currency = "NGN";
//        BigDecimal fee = calculateFee(amount);
//        BigDecimal totalAmount = amount.add(fee);
//
//        boolean sufficient = glService.hasSufficientBalance(user, totalAmount);
//        log.info("Checking if user {} has sufficient balance for amount {} + fee {}: {}",
//                user.getId(), amount, fee, sufficient);
//
//        return sufficient;
//    }
//
//    /**
//     * Process payment for an order
//     * @param user the user making the payment
//     * @param order the order to process
//     * @return the created transaction
//     */
//    @Override
//    @Transactional
//    public Transaction processPayment(User user, Order order) {
//        log.info("Processing payment for user {} and order {}", user.getId(), order.getId());
//
//        BigDecimal amount = order.getAmount();
//        BigDecimal fee = calculateFee(amount);
//
//        // Create transaction record
//        Transaction transaction = new Transaction();
//        transaction.setUser(user);
//        transaction.setOrder(order);
//        transaction.setTransactionType("WITHDRAWAL");
//        transaction.setAmount(amount);
//        transaction.setFee(fee);
//        transaction.setCurrency(order.getCurrencyId());
//        transaction.setStatus("PENDING");
//
//        // Deduct from user's balance
//        glService.debitUserAccount(user, amount.add(fee));
//        log.info("Debited {} + {} fee from user {} account", amount, fee, user.getId());
//
//        // Credit fee to platform account
//        glService.creditFeeAccount(fee);
//        log.info("Credited {} fee to platform account", fee);
//
//        // Process the actual payment via third-party provider(PalmpayPaymentService)
//        String reference = palmpayPaymentGatewayService.processPayment(
//                order.getTargetNickName(),
//                amount,
//                order.getCurrencyId(),
//                "Payment for order " + order.getId()
//        );
//
//        transaction.setExternalReference(reference);
//        transaction.setStatus("COMPLETED");
//        transaction.setCompletedAt(LocalDateTime.now());
//
//        // Generate receipt
//        String receiptUrl = palmpayPaymentGatewayService.generateReceipt(reference);
//        transaction.setReceiptUrl(receiptUrl);
//
//        Transaction savedTransaction = transactionRepository.save(transaction);
//        log.info("Saved transaction {} for order {}", savedTransaction.getId(), order.getId());
//
//        // Publish payment processed event
//        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
//                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), order.getId()));
//
//        return savedTransaction;
//    }
//
//    /**
//     * Process payment for a buy order
//     * @param user the user making the payment
//     * @param order the order to process
//     * @return the created transaction
//     */
//    @Override
//    @Transactional
//    public Transaction processBuyOrderPayment(User user, Order order) {
//        log.info("Processing buy order payment for user {} and order {}", user.getId(), order.getId());
//
//        BigDecimal amount = order.getAmount();
//        BigDecimal fee = calculateFee(amount);
//        String currency = order.getCurrencyId();
//
//        // Create transaction record
//        Transaction transaction = new Transaction();
//        transaction.setUser(user);
//        transaction.setOrder(order);
//        transaction.setTransactionType("WITHDRAWAL");
//        transaction.setAmount(amount);
//        transaction.setFee(fee);
//        transaction.setCurrency(order.getCurrencyId());
//        transaction.setStatus("PENDING");
//
//        // Extract payment details from order
//        // In a real implementation, this would come from the order details or user input
//        // For now, we'll use a mock format
//        String paymentMethod = "BANK_TRANSFER";
//        String paymentDetails = "057:1234567890"; // Format: "bankCode:accountNumber"
//
//        transaction.setPaymentMethod(paymentMethod);
//        transaction.setPaymentDetails(paymentDetails);
//
//        // Check wallet balance
//        if (!walletBalanceService.hasSufficientBalance(user, currency, amount.add(fee))) {
//            throw new InsufficientBalanceException("Insufficient balance in " + currency + " wallet");
//        }
//
//        // Create transaction record
//        Transaction transaction = new Transaction();
//
//        // Debit from user's wallet balance
//        walletBalanceService.debitWallet(user, currency, amount.add(fee));
//
//        // Debit from user's GL balance for audit trail
//        glService.debitUserAccount(user, amount.add(fee));
//
//        // Credit fee to platform account
//        glService.creditFeeAccount(fee);
//        log.info("Credited {} fee to platform account", fee);
//
//        // Set transaction as completed
//        transaction.setStatus("COMPLETED");
//        transaction.setCompletedAt(LocalDateTime.now());
//
//        // Generate receipt
//        String receiptUrl = palmpayPaymentGatewayService.generateReceipt("BUY-" + order.getId());
//        transaction.setReceiptUrl(receiptUrl);
//        log.info("Generated receipt at {} for order {}", receiptUrl, order.getId());
//
//        Transaction savedTransaction = transactionRepository.save(transaction);
//        log.info("Saved transaction {} for order {}", savedTransaction.getId(), order.getId());
//
//        // Publish payment processed event
//        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
//                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), order.getId()));
//        log.info("Published PaymentProcessedEvent for transaction {} and order {}",
//                savedTransaction.getId(), order.getId());
//
//        return savedTransaction;
//    }
//
//    /**
//     * Process a deposit from Palmpay webhook
//     * @param virtualAccount the virtual account receiving the deposit
//     * @param amount the amount being deposited
//     * @param orderReference the Palmpay order reference
//     * @param payerDetails details of the payer
//     * @return the created transaction
//     */
//    @Override
//    @Transactional
//    public Transaction processPalmpayDeposit(VirtualAccount virtualAccount, BigDecimal amount, String orderReference, Map<String, String> payerDetails) {
//        User user = virtualAccount.getUser();
//        String currency = virtualAccount.getCurrency();
//
//        log.info("Processing deposit of {} {} to virtual account {} for user {}",
//                amount, currency, virtualAccount.getAccountNumber(), user.getId());
//
//        // Create transaction record
//        Transaction transaction = new Transaction();
//        transaction.setUser(user);
//        transaction.setVirtualAccount(virtualAccount);
//        transaction.setTransactionType("DEPOSIT");
//        transaction.setAmount(amount);
//        transaction.setFee(BigDecimal.ZERO); // No fee for deposits
//        transaction.setCurrency(virtualAccount.getCurrency());
//        transaction.setStatus("COMPLETED");
//        transaction.setExternalReference(orderReference);
//
//
//        // Set payment details if provided
//        if (payerDetails != null && !payerDetails.isEmpty()) {
//            transaction.setPaymentMethod("BANK_TRANSFER");
//
//            // Create a formatted string with the payer details
//            StringBuilder paymentDetailsBuilder = new StringBuilder();
//            if (payerDetails.containsKey("bankName")) {
//                paymentDetailsBuilder.append(payerDetails.get("bankName")).append(" - ");
//            }
//            if (payerDetails.containsKey("accountNo")) {
//                paymentDetailsBuilder.append(payerDetails.get("accountNo")).append(" - ");
//            }
//            if (payerDetails.containsKey("accountName")) {
//                paymentDetailsBuilder.append(payerDetails.get("accountName"));
//            }
//
//            transaction.setPaymentDetails(paymentDetailsBuilder.toString());
//        }
//
//        // Credit user's wallet balance
//        walletBalanceService.creditWallet(user, currency, amount);
//
//        transaction.setCompletedAt(LocalDateTime.now());
//
//        // Credit user's balance in GL system
//        glService.creditUserAccount(user, amount);
//        log.info("Credited {} to user {} account", amount, user.getId());
//
//        Transaction savedTransaction = transactionRepository.save(transaction);
//        log.info("Saved deposit transaction {}", savedTransaction.getId());
//
//        // Publish payment processed event
//        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
//                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), null));
//
//        return savedTransaction;
//
//    }
//
//    @Override
//    @Transactional
//    public Transaction processDeposit(VirtualAccount virtualAccount, BigDecimal amount, String reference) {
//        // Delegate to processPalmpayDeposit with null payerDetails
//        return processPalmpayDeposit(virtualAccount, amount, reference, null);
//    }
//
//
//    /**
//     * Get receipt data for a transaction
//     * @param transaction the transaction to get receipt for
//     * @return the receipt data as byte array
//     */
//    @Override
//    public byte[] getReceiptData(Transaction transaction) {
//        log.info("Getting receipt data for transaction {}", transaction.getId());
//
//        if (transaction.getReceiptUrl() == null) {
//            log.warn("No receipt URL found for transaction {}", transaction.getId());
//            return new byte[0];
//        }
//
//        return palmpayPaymentGatewayService.getReceiptData(transaction.getReceiptUrl());
//    }
//
//    /**
//     * Find a transaction by external reference
//     * @param reference the external reference to search for
//     * @return the found transaction or null
//     */
//    @Override
//    public Transaction findTransaction(String reference) {
//        log.info("Finding transaction with reference: {}", reference);
//
//        return transactionRepository.findByExternalReference(reference)
//                .orElse(null);
//    }
//}


package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.event.PaymentProcessedEvent;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final TransactionRepository transactionRepository;
    private final GLService glService;
    private final PalmpayPaymentGatewayService palmpayPaymentGatewayService;
    private final RabbitTemplate rabbitTemplate;
    private final WalletBalanceService walletBalanceService;
    private final ReceiptService receiptService;

    // Fee configuration (could be moved to application.yml)
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.012"); // 1.2%
    private static final BigDecimal FEE_CAP_NGN = new BigDecimal("1200.00");
    private static final BigDecimal FEE_CAP_USD = new BigDecimal("5.00");
    private static final BigDecimal FEE_CAP_EUR = new BigDecimal("4.50");
    private static final BigDecimal FEE_CAP_GBP = new BigDecimal("4.00");
    private static final BigDecimal FEE_CAP_USDT = new BigDecimal("5.00");

    /**
     * Calculate the fee for a given amount (defaults to NGN)
     * @param amount the amount to calculate fee for
     * @return the calculated fee
     */
    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        // Default to NGN for backward compatibility
        return calculateFee(amount, "NGN");
    }

    /**
     * Calculate the fee for a given amount and currency
     * @param amount the amount to calculate fee for
     * @param currency the currency
     * @return the calculated fee
     */
    @Override
    public BigDecimal calculateFee(BigDecimal amount, String currency) {
        // Calculate fee as percentage of the amount
        BigDecimal fee = amount.multiply(FEE_PERCENTAGE);

        // Apply currency-specific cap
        BigDecimal feeCap = getFeeCapForCurrency(currency);
        if (fee.compareTo(feeCap) > 0) {
            fee = feeCap;
        }

        log.info("Calculated fee for amount {} {}: {}", amount, currency, fee);
        return fee;
    }

    private BigDecimal getFeeCapForCurrency(String currency) {
        switch (currency.toUpperCase()) {
            case "NGN":
                return FEE_CAP_NGN;
            case "USD":
                return FEE_CAP_USD;
            case "EUR":
                return FEE_CAP_EUR;
            case "GBP":
                return FEE_CAP_GBP;
            case "USDT":
                return FEE_CAP_USDT;
            default:
                log.warn("No fee cap defined for currency {}, using NGN cap", currency);
                return FEE_CAP_NGN;
        }
    }

    /**
     * Check if a user has sufficient balance (defaults to NGN)
     * @param user the user to check
     * @param amount the amount to check
     * @return true if the user has sufficient balance
     */
    @Override
    public boolean hasSufficientBalance(User user, BigDecimal amount) {
        // Default to NGN for backward compatibility
        return hasSufficientBalance(user, amount, "NGN");
    }

    /**
     * Check if a user has sufficient balance for a transaction
     * @param user the user to check
     * @param amount the amount to check
     * @param currency the currency
     * @return true if the user has sufficient balance
     */
    @Override
    public boolean hasSufficientBalance(User user, BigDecimal amount, String currency) {
        BigDecimal fee = calculateFee(amount, currency);
        BigDecimal totalAmount = amount.add(fee);

        boolean sufficient = walletBalanceService.hasSufficientBalance(user, currency, totalAmount);
        log.info("Checking if user {} has sufficient balance for amount {} + fee {} {}: {}",
                user.getId(), amount, fee, currency, sufficient);

        return sufficient;
    }

    /**
     * Process payment for an order
     * @param user the user making the payment
     * @param order the order to process
     * @return the created transaction
     */
    @Override
    @Transactional
    public Transaction processPayment(User user, Order order) {
        log.info("Processing payment for user {} and order {}", user.getId(), order.getId());

        BigDecimal amount = order.getAmount();
        String currency = order.getCurrencyId();
        BigDecimal fee = calculateFee(amount, currency);

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(order);
        transaction.setTransactionType("WITHDRAWAL");
        transaction.setAmount(amount);
        transaction.setFee(fee);
        transaction.setCurrency(currency);
        transaction.setStatus("PENDING");

        // Deduct from user's wallet balance
        walletBalanceService.debitWallet(user, currency, amount.add(fee));
        log.info("Debited {} + {} fee from user {} {} wallet", amount, fee, user.getId(), currency);

        // Also deduct from GL for audit trail
        glService.debitUserAccount(user, amount.add(fee));

        // Credit fee to platform account
        glService.creditFeeAccount(fee);
        log.info("Credited {} {} fee to platform account", fee, currency);

        // Process the actual payment via third-party provider
        String reference = palmpayPaymentGatewayService.processPayment(
                order.getTargetNickName(),
                amount,
                currency,
                "Payment for order " + order.getId()
        );

        transaction.setExternalReference(reference);
        transaction.setStatus("COMPLETED");
        transaction.setCompletedAt(LocalDateTime.now());

        // Generate receipt
        String receiptUrl = palmpayPaymentGatewayService.generateReceipt(reference);
        transaction.setReceiptUrl(receiptUrl);

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Saved transaction {} for order {}", savedTransaction.getId(), order.getId());

        // Publish payment processed event
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), order.getId()));

        return savedTransaction;
    }

    private void sendWithdrawalNotification(User user, BigDecimal amount, String currency, Long transactionId, BigDecimal fee) {
        String title = "Withdrawal Processed - Transaction #" + transactionId;
        String message = String.format(
                "Dear %s,\n\n" +
                        "Your withdrawal has been successfully processed:\n\n" +
                        "Amount: %s %s\n" +
                        "Fee: %s %s\n" +
                        "Total Deducted: %s %s\n" +
                        "Transaction ID: %s\n" +
                        "Status: Completed\n\n" +
                        "The funds have been transferred from your %s wallet.\n\n" +
                        "Best regards,\n" +
                        "Papaymoni Team",
                user.getFirstName(),
                amount.setScale(2, RoundingMode.HALF_UP),
                currency,
                fee.setScale(2, RoundingMode.HALF_UP),
                currency,
                amount.add(fee).setScale(2, RoundingMode.HALF_UP),
                currency,
                transactionId,
                currency
        );

        NotificationEvent notificationEvent = new NotificationEvent(
                user.getId(), "EMAIL", title, message);

        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY, notificationEvent);
        log.info("Sent withdrawal notification to user {}", user.getId());
    }

    /**
     * Process payment for a buy order
     * @param user the user making the payment
     * @param order the order to process
     * @return the created transaction
     */
    @Override
    @Transactional
    public Transaction processBuyOrderPayment(User user, Order order) {
        log.info("Processing buy order payment for user {} and order {}", user.getId(), order.getId());

        BigDecimal amount = order.getAmount();
        String currency = order.getCurrencyId();
        BigDecimal fee = calculateFee(amount, currency);

        // Check wallet balance
        if (!walletBalanceService.hasSufficientBalance(user, currency, amount.add(fee))) {
            throw new InsufficientBalanceException("Insufficient balance in " + currency + " wallet");
        }

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(order);
        transaction.setTransactionType("WITHDRAWAL");
        transaction.setAmount(amount);
        transaction.setFee(fee);
        transaction.setCurrency(currency);
        transaction.setStatus("PENDING");

        // Extract payment details from order
        String paymentMethod = "BANK_TRANSFER";
        String paymentDetails = "057:1234567890"; // This should come from order details in real implementation

        transaction.setPaymentMethod(paymentMethod);
        transaction.setPaymentDetails(paymentDetails);

        // Deduct from user's wallet balance
        walletBalanceService.debitWallet(user, currency, amount.add(fee));
        log.info("Debited {} + {} fee from user {} {} wallet", amount, fee, user.getId(), currency);

        // Also deduct from GL for audit trail
        glService.debitUserAccount(user, amount.add(fee));

        // Credit fee to platform account
        glService.creditFeeAccount(fee);
        log.info("Credited {} {} fee to platform account", fee, currency);

        // Set transaction as completed
        transaction.setStatus("COMPLETED");
        transaction.setCompletedAt(LocalDateTime.now());

        // Generate receipt
        String receiptUrl = palmpayPaymentGatewayService.generateReceipt("BUY-" + order.getId());
        transaction.setReceiptUrl(receiptUrl);
        log.info("Generated receipt at {} for order {}", receiptUrl, order.getId());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Saved transaction {} for order {}", savedTransaction.getId(), order.getId());

        // Send withdrawal notification
        sendWithdrawalNotification(user, amount, currency, savedTransaction.getId(), fee);

        // Publish payment processed event
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), order.getId()));
        log.info("Published PaymentProcessedEvent for transaction {} and order {}",
                savedTransaction.getId(), order.getId());

        return savedTransaction;
    }

    /**
     * Process a deposit from Palmpay webhook
     * @param virtualAccount the virtual account receiving the deposit
     * @param amount the amount being deposited
     * @param orderReference the Palmpay order reference
     * @param payerDetails details of the payer
     * @return the created transaction
     */
    @Override
    @Transactional
    public Transaction processPalmpayDeposit(VirtualAccount virtualAccount, BigDecimal amount, String orderReference, Map<String, String> payerDetails) {
        User user = virtualAccount.getUser();
        String currency = virtualAccount.getCurrency();

        log.info("Processing deposit of {} {} to virtual account {} for user {}",
                amount, currency, virtualAccount.getAccountNumber(), user.getId());

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setVirtualAccount(virtualAccount);
        transaction.setTransactionType("DEPOSIT");
        transaction.setAmount(amount);
        transaction.setFee(BigDecimal.ZERO); // No fee for deposits
        transaction.setCurrency(currency);
        transaction.setStatus("COMPLETED");
        transaction.setExternalReference(orderReference);

        // Set payment details if provided
        if (payerDetails != null && !payerDetails.isEmpty()) {
            transaction.setPaymentMethod("BANK_TRANSFER");

            // Create a formatted string with the payer details
            StringBuilder paymentDetailsBuilder = new StringBuilder();
            if (payerDetails.containsKey("bankName")) {
                paymentDetailsBuilder.append(payerDetails.get("bankName")).append(" - ");
            }
            if (payerDetails.containsKey("accountNo")) {
                paymentDetailsBuilder.append(payerDetails.get("accountNo")).append(" - ");
            }
            if (payerDetails.containsKey("accountName")) {
                paymentDetailsBuilder.append(payerDetails.get("accountName"));
            }

            transaction.setPaymentDetails(paymentDetailsBuilder.toString());
        }

        transaction.setCompletedAt(LocalDateTime.now());

        // Credit user's wallet balance
        walletBalanceService.creditWallet(user, currency, amount);
        log.info("Credited {} {} to user {} {} wallet", amount, currency, user.getId(), currency);

        // Also credit GL for audit trail
        glService.creditUserAccount(user, amount);

        Transaction savedTransaction = transactionRepository.save(transaction);
        // Generate receipt
        try {
            String receiptUrl = receiptService.generateReceipt(savedTransaction);
            savedTransaction.setReceiptUrl(receiptUrl);
            savedTransaction = transactionRepository.save(savedTransaction);
            log.info("Generated receipt for transaction {}: {}", savedTransaction.getId(), receiptUrl);
        } catch (Exception e) {
            log.error("Failed to generate receipt for transaction {}: {}", savedTransaction.getId(), e.getMessage());
        }
        log.info("Saved deposit transaction {}", savedTransaction.getId());

        // Publish payment processed event
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), null));

        return savedTransaction;
    }

    @Override
    @Transactional
    public Transaction processDeposit(VirtualAccount virtualAccount, BigDecimal amount, String reference) {
        // Delegate to processPalmpayDeposit with null payerDetails
        return processPalmpayDeposit(virtualAccount, amount, reference, null);
    }

    /**
     * Get receipt data for a transaction
     * @param transaction the transaction to get receipt for
     * @return the receipt data as byte array
     */
    @Override
    public byte[] getReceiptData(Transaction transaction) {
        log.info("Getting receipt data for transaction {}", transaction.getId());

        if (transaction.getReceiptUrl() == null) {
            log.warn("No receipt URL found for transaction {}", transaction.getId());
            return new byte[0];
        }

        return palmpayPaymentGatewayService.getReceiptData(transaction.getReceiptUrl());
    }

    /**
     * Find a transaction by external reference
     * @param reference the external reference to search for
     * @return the found transaction or null
     */
    @Override
    public Transaction findTransaction(String reference) {
        log.info("Finding transaction with reference: {}", reference);

        return transactionRepository.findByExternalReference(reference)
                .orElse(null);
    }
}
