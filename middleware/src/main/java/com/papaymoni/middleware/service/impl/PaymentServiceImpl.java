package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.event.PaymentProcessedEvent;
import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.GLService;
import com.papaymoni.middleware.service.PaymentService;
import com.papaymoni.middleware.service.PalmpayPaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_EXCHANGE;
import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_PROCESSED_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final TransactionRepository transactionRepository;
    private final GLService glService;
    private final PalmpayPaymentGatewayService palmpayPaymentGatewayService;
    private final RabbitTemplate rabbitTemplate;

    // Fee is 1.2% capped at 1,200 NGN
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.012");
    private static final BigDecimal FEE_CAP = new BigDecimal("1200.00");
    private static final String FEE_CURRENCY = "NGN";

    /**
     * Calculate the fee for a given amount
     * Fee is 1.2% capped at 1,200 NGN
     * @param amount the amount to calculate fee for
     * @return the calculated fee
     */
    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        // Calculate fee as 1.2% of the amount
        BigDecimal fee = amount.multiply(FEE_PERCENTAGE);

        // If the fee currency is not NGN, we would need to convert it
        // For now, we'll assume the fee is already in NGN
        BigDecimal feeInNGN = fee;

        // Apply the cap if the fee exceeds 1,200 NGN
        if (feeInNGN.compareTo(FEE_CAP) > 0) {
            fee = FEE_CAP;
        }

        log.info("Calculated fee for amount {}: {}", amount, fee);
        return fee;
    }

    /**
     * Check if a user has sufficient balance for a transaction
     * @param user the user to check
     * @param amount the amount to check
     * @return true if the user has sufficient balance
     */
    @Override
    public boolean hasSufficientBalance(User user, BigDecimal amount) {
        BigDecimal fee = calculateFee(amount);
        BigDecimal totalAmount = amount.add(fee);

        boolean sufficient = glService.hasSufficientBalance(user, totalAmount);
        log.info("Checking if user {} has sufficient balance for amount {} + fee {}: {}",
                user.getId(), amount, fee, sufficient);

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
        BigDecimal fee = calculateFee(amount);

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(order);
        transaction.setTransactionType("WITHDRAWAL");
        transaction.setAmount(amount);
        transaction.setFee(fee);
        transaction.setCurrency(order.getCurrencyId());
        transaction.setStatus("PENDING");

        // Deduct from user's balance
        glService.debitUserAccount(user, amount.add(fee));
        log.info("Debited {} + {} fee from user {} account", amount, fee, user.getId());

        // Credit fee to platform account
        glService.creditFeeAccount(fee);
        log.info("Credited {} fee to platform account", fee);

        // Process the actual payment via third-party provider(PalmpayPaymentService)
        String reference = palmpayPaymentGatewayService.processPayment(
                order.getTargetNickName(),
                amount,
                order.getCurrencyId(),
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
        BigDecimal fee = calculateFee(amount);

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(order);
        transaction.setTransactionType("WITHDRAWAL");
        transaction.setAmount(amount);
        transaction.setFee(fee);
        transaction.setCurrency(order.getCurrencyId());
        transaction.setStatus("PENDING");

        // Extract payment details from order
        // In a real implementation, this would come from the order details or user input
        // For now, we'll use a mock format
        String paymentMethod = "BANK_TRANSFER";
        String paymentDetails = "057:1234567890"; // Format: "bankCode:accountNumber"

        transaction.setPaymentMethod(paymentMethod);
        transaction.setPaymentDetails(paymentDetails);

        // Deduct from user's balance
        glService.debitUserAccount(user, amount.add(fee));
        log.info("Debited {} + {} fee from user {} account", amount, fee, user.getId());

        // Credit fee to platform account
        glService.creditFeeAccount(fee);
        log.info("Credited {} fee to platform account", fee);

        // Set transaction as completed
        transaction.setStatus("COMPLETED");
        transaction.setCompletedAt(LocalDateTime.now());

        // Generate receipt
        String receiptUrl = palmpayPaymentGatewayService.generateReceipt("BUY-" + order.getId());
        transaction.setReceiptUrl(receiptUrl);
        log.info("Generated receipt at {} for order {}", receiptUrl, order.getId());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Saved transaction {} for order {}", savedTransaction.getId(), order.getId());

        // Publish payment processed event
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), order.getId()));
        log.info("Published PaymentProcessedEvent for transaction {} and order {}",
                savedTransaction.getId(), order.getId());

        return savedTransaction;
    }

    /**
     * Process a deposit to a virtual account
     * @param virtualAccount the virtual account receiving the deposit
     * @param amount the amount being deposited
     * @param reference the external reference for the deposit
     * @return the created transaction
     */
    @Override
    @Transactional
    public Transaction processDeposit(VirtualAccount virtualAccount, BigDecimal amount, String reference) {
        User user = virtualAccount.getUser();
        log.info("Processing deposit of {} to virtual account {} for user {}",
                amount, virtualAccount.getId(), user.getId());

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setVirtualAccount(virtualAccount);
        transaction.setTransactionType("DEPOSIT");
        transaction.setAmount(amount);
        transaction.setFee(BigDecimal.ZERO); // No fee for deposits
        transaction.setCurrency(virtualAccount.getCurrency());
        transaction.setStatus("COMPLETED");
        transaction.setExternalReference(reference);
        transaction.setCompletedAt(LocalDateTime.now());
        //transaction.setDescription("Deposit to virtual account " + virtualAccount.getAccountNumber());

        // Credit user's balance
        glService.creditUserAccount(user, amount);
        log.info("Credited {} to user {} account", amount, user.getId());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Saved deposit transaction {}", savedTransaction.getId());

        // Publish payment processed event
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(savedTransaction.getId(), user.getId(), null));

        return savedTransaction;
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
