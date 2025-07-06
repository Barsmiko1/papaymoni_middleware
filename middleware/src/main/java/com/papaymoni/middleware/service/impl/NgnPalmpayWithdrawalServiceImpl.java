package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class NgnPalmpayWithdrawalServiceImpl implements NgnPalmpayWithdrawalService {

    // Fee configuration
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.012"); // 1.2%
    private static final BigDecimal FEE_CAP_NGN = new BigDecimal("1200.00");
    // Track transactions that have already sent notifications to prevent duplicates
    private final Set<Long> notifiedTransactions = Collections.synchronizedSet(new HashSet<>());

    private final WalletBalanceService walletBalanceService;
    private final PaymentService paymentService;
    private final GLService glService;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final VirtualAccountService virtualAccountService;
    private final ReceiptService receiptService;

    // Use @Lazy to break the circular dependency
    private final PalmpayPayoutGatewayService palmpayPayoutGatewayService;

    @Autowired
    public NgnPalmpayWithdrawalServiceImpl(
            WalletBalanceService walletBalanceService,
            PaymentService paymentService,
            GLService glService,
            TransactionRepository transactionRepository,
            NotificationService notificationService,
            VirtualAccountService virtualAccountService,
            ReceiptService receiptService,
            @Lazy PalmpayPayoutGatewayService palmpayPayoutGatewayService) {
        this.walletBalanceService = walletBalanceService;
        this.paymentService = paymentService;
        this.glService = glService;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
        this.virtualAccountService = virtualAccountService;
        this.receiptService = receiptService;
        this.palmpayPayoutGatewayService = palmpayPayoutGatewayService;
    }

    @Override
    public List<BankDto> getAvailableBanks() {
        return palmpayPayoutGatewayService.queryBankList();
    }

    @Override
    public BankAccountQueryDto performNameEnquiry(String bankCode, String accountNumber) {
        return palmpayPayoutGatewayService.queryBankAccount(bankCode, accountNumber);
    }

    /**
     * Calculate withdrawal fee based on the configured percentage and cap
     * @param amount The withdrawal amount
     * @param currency The currency (fee cap only applies to NGN)
     * @return The calculated fee
     */
    private BigDecimal calculateFee(BigDecimal amount, String currency) {
        // Calculate fee as percentage of amount
        BigDecimal fee = amount.multiply(FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);

        // Apply fee cap for NGN currency
        if ("NGN".equalsIgnoreCase(currency) && fee.compareTo(FEE_CAP_NGN) > 0) {
            fee = FEE_CAP_NGN;
        }

        return fee;
    }

    @Override
    @Transactional
    public ApiResponse<Transaction> initiateWithdrawal(User user, WithdrawalRequestDto withdrawalRequest) {
        log.info("Inside initiateWithdrawal method");
        log.info("Inside initiateWithdrawal method: {} and {}", user.toString(), withdrawalRequest.toString());
        log.info("Initiating withdrawal for user: {}, amount: {} {}",
                user.getId(), withdrawalRequest.getAmount(), withdrawalRequest.getCurrency());

        try {
            // Validate the withdrawal amount
            BigDecimal amount = withdrawalRequest.getAmount();
            String currency = withdrawalRequest.getCurrency();

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid withdrawal amount: {}", amount);
                return ApiResponse.error("Withdrawal amount must be greater than zero");
            }

            // Calculate fee using the configured percentage and cap
            BigDecimal fee = calculateFee(amount, currency);
            BigDecimal totalAmount = amount.add(fee);

            log.info("Withdrawal amount: {}, fee: {}, total: {}", amount, fee, totalAmount);

            // Check user balance
            if (!paymentService.hasSufficientBalance(user, totalAmount, currency)) {
                log.warn("Insufficient balance for withdrawal: user {}, required: {} {}",
                        user.getId(), totalAmount, currency);
                return ApiResponse.error("Insufficient balance to complete this withdrawal");
            }

            // Find user's virtual account for this currency
            List<VirtualAccount> userAccounts = virtualAccountService.getUserVirtualAccountsByCurrency(user, currency);
            if (userAccounts.isEmpty()) {
                return ApiResponse.error("No virtual account found for currency: " + currency);
            }
            VirtualAccount virtualAccount = userAccounts.get(0);

            // Generate unique order ID for tracking
            String orderId = generateOrderId(user.getId());

            // Create a pending transaction record
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setTransactionType("WITHDRAWAL");
            transaction.setAmount(amount);
            transaction.setFee(fee);
            transaction.setCurrency(currency);
            transaction.setStatus("PENDING");
            // Use orderId as external reference
            transaction.setExternalReference(orderId);
            transaction.setVirtualAccount(virtualAccount);

            transaction.setPaymentMethod("BANK_TRANSFER");
            transaction.setPaymentDetails(withdrawalRequest.getBankCode() + ":" + withdrawalRequest.getAccountNumber());
            transaction.setCreatedAt(LocalDateTime.now());

            Transaction savedTransaction = transactionRepository.save(transaction);
            log.info("Created pending withdrawal transaction: {}", savedTransaction.getId());

            // Deduct from user's wallet balance
            walletBalanceService.debitWallet(user, currency, totalAmount);
            log.info("Debited {} {} from user {} wallet", totalAmount, currency, user.getId());

            // Update virtual account balance
            BigDecimal newBalance = virtualAccount.getBalance().subtract(totalAmount);
            virtualAccountService.updateAccountBalance(virtualAccount, newBalance);
            log.info("Updated virtual account {} balance to {}", virtualAccount.getAccountNumber(), newBalance);

            // Also deduct from GL for audit trail
            glService.debitUserAccount(user, totalAmount);

            // Credit fee to platform account
            glService.creditFeeAccount(fee);
            log.info("Credited {} {} fee to platform account", fee, currency);

            // Initiate the payout via PalmPay
            PalmpayPayoutResponseDto payoutResponse = palmpayPayoutGatewayService.initiatePayoutTransaction(
                    orderId,
                    withdrawalRequest.getAccountName(),
                    withdrawalRequest.getBankCode(),
                    withdrawalRequest.getAccountNumber(),
                    withdrawalRequest.getPhoneNumber(),
                    amount,
                    currency,
                    withdrawalRequest.getRemark()
            );

            if (payoutResponse.isSuccess()) {
                // Update transaction with PalmPay orderNo
                savedTransaction.setExternalReference(payoutResponse.getData().getOrderId());
                savedTransaction.setPaymentDetails(savedTransaction.getPaymentDetails() +
                        " | PalmPay OrderNo: " + payoutResponse.getData().getOrderNo());

                // If PalmPay returns immediate success, mark transaction as completed
                if (payoutResponse.getData().getOrderStatus() == 2) {
                    Map<String, Object> responseData = new HashMap<>();
                    responseData.put("orderStatus", payoutResponse.getData().getOrderStatus());
                    responseData.put("orderNo", payoutResponse.getData().getOrderNo());
                    responseData.put("sessionId", payoutResponse.getData().getSessionId());
                    savedTransaction = completeWithdrawal(savedTransaction, responseData);
                } else {
                    // Otherwise, leave as PENDING - webhook will update later
                    savedTransaction.setStatus("PROCESSING");
                    savedTransaction = transactionRepository.save(savedTransaction);
                }

                return ApiResponse.success("Withdrawal initiated successfully", savedTransaction);
            } else {
                // Handle failed initiation
                log.error("Failed to initiate withdrawal: {}", payoutResponse.getRespMsg());

                // Refund user's wallet
                walletBalanceService.creditWallet(user, currency, totalAmount);

                // Update virtual account balance back
                BigDecimal refundedBalance = virtualAccount.getBalance().add(totalAmount);
                virtualAccountService.updateAccountBalance(virtualAccount, refundedBalance);

                // Update GL entries
                glService.creditUserAccount(user, totalAmount);
                glService.debitFeeAccount(fee);

                // Update transaction status
                savedTransaction.setStatus("FAILED");
                savedTransaction.setUpdatedAt(LocalDateTime.now());
                savedTransaction = transactionRepository.save(savedTransaction);

                return ApiResponse.error("Withdrawal failed: " + payoutResponse.getRespMsg());
            }
        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance for withdrawal: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing withdrawal: {}", e.getMessage(), e);
            return ApiResponse.error("An error occurred while processing your withdrawal: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Transaction completeWithdrawal(Transaction transaction, Map<String, Object> payoutResponse) {
        log.info("Completing withdrawal transaction: {}", transaction.getId());

        // Update transaction with response details
        if (payoutResponse.containsKey("orderNo")) {
            String orderNo = payoutResponse.get("orderNo").toString();
            transaction.setPaymentDetails(transaction.getPaymentDetails() + " | OrderNo: " + orderNo);
        }

        // Mark as completed
        transaction.setStatus("COMPLETED");
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        Transaction updatedTransaction = transactionRepository.save(transaction);
        log.info("Withdrawal transaction {} marked as completed", transaction.getId());

        // Generate receipt
        try {
            String receiptUrl = receiptService.generateReceipt(updatedTransaction);
            if (receiptUrl != null) {
                updatedTransaction.setReceiptUrl(receiptUrl);
                updatedTransaction = transactionRepository.save(updatedTransaction);
                log.info("Generated receipt for transaction {}: {}", updatedTransaction.getId(), receiptUrl);
            }
        } catch (Exception e) {
            log.error("Failed to generate receipt for transaction {}: {}", updatedTransaction.getId(), e.getMessage());
        }

        // Send notification to user
        try {
            // Send notification to user only if we haven't already
            if (!notifiedTransactions.contains(transaction.getId())) {
                notificationService.sendWithdrawalNotification(
                        transaction.getUser(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getId(),
                        transaction.getFee()
                );
                notifiedTransactions.add(transaction.getId());
            }

            log.info("Sent withdrawal notification to user {}", transaction.getUser().getId());

        } catch (Exception e) {
            log.info("Failed to send withdrawal notification: {}", e.getMessage());
        }
        // Save the updated transaction
        return updatedTransaction;
    }

    @Override
    @Transactional
    public Transaction handleFailedWithdrawal(Transaction transaction, String errorReason) {
        log.info("Handling failed withdrawal transaction: {}", transaction.getId());

        User user = transaction.getUser();
        String currency = transaction.getCurrency();
        BigDecimal amount = transaction.getAmount();
        BigDecimal fee = transaction.getFee();
        BigDecimal totalAmount = amount.add(fee);

        // Update transaction status
        transaction.setStatus("FAILED");
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setPaymentDetails(transaction.getPaymentDetails() + " | Error: " + errorReason);

        Transaction updatedTransaction = transactionRepository.save(transaction);

        // Refund user's wallet balance
        walletBalanceService.creditWallet(user, currency, totalAmount);
        log.info("Refunded {} {} to user {} wallet", totalAmount, currency, user.getId());

        // Update virtual account balance
        VirtualAccount virtualAccount = transaction.getVirtualAccount();
        if (virtualAccount != null) {
            BigDecimal newBalance = virtualAccount.getBalance().add(totalAmount);
            virtualAccountService.updateAccountBalance(virtualAccount, newBalance);
            log.info("Updated virtual account {} balance to {}", virtualAccount.getAccountNumber(), newBalance);
        }

        // Update GL entries
        glService.creditUserAccount(user, totalAmount);
        glService.debitFeeAccount(fee);

        // Notify the user
        try {
            String title = "Withdrawal Failed - Transaction #" + transaction.getId();
            String message = String.format(
                    "Dear %s,\n\n" +
                            "We were unable to process your withdrawal:\n\n" +
                            "Amount: %s %s\n" +
                            "Fee: %s %s\n" +
                            "Transaction ID: %s\n" +
                            "Reason: %s\n\n" +
                            "The funds have been returned to your %s wallet.\n\n" +
                            "Best regards,\n" +
                            "Papaymoni Team",
                    user.getFirstName(),
                    amount,
                    currency,
                    fee,
                    currency,
                    transaction.getId(),
                    errorReason,
                    currency
            );

            notificationService.createNotification(user, "EMAIL", title, message);
        } catch (Exception e) {
            log.error("Failed to send withdrawal failure notification: {}", e.getMessage());
        }

        return updatedTransaction;
    }

    @Override
    @Transactional
    public Transaction checkAndUpdateTransactionStatus(Long transactionId) {
        log.info("Checking status for transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));

        // Only check pending or processing transactions
        if (!"PENDING".equals(transaction.getStatus()) && !"PROCESSING".equals(transaction.getStatus())) {
            log.info("Transaction {} is already in {} state, skipping status check", transactionId, transaction.getStatus());
            return transaction;
        }

        try {
            // Extract orderNo from payment details if available
            String orderNo = null;
            String paymentDetails = transaction.getPaymentDetails();
            if (paymentDetails != null && paymentDetails.contains("OrderNo:")) {
                orderNo = paymentDetails.substring(paymentDetails.indexOf("OrderNo:") + 8).trim();
                if (orderNo.contains("|")) {
                    orderNo = orderNo.substring(0, orderNo.indexOf("|")).trim();
                }
            }

            if (orderNo == null) {
                log.warn("Cannot check status for transaction {} - missing OrderNo", transactionId);
                return transaction;
            }

            // Query status from PalmPay
            PalmpayPayoutResponseDto statusResponse = palmpayPayoutGatewayService.queryTransactionStatus(
                    transaction.getExternalReference(), orderNo);

            if (statusResponse.isSuccess()) {
                Integer orderStatus = statusResponse.getData().getOrderStatus();

                Map<String, Object> responseData = new HashMap<>();
                responseData.put("orderStatus", orderStatus);
                responseData.put("orderNo", statusResponse.getData().getOrderNo());
                responseData.put("sessionId", statusResponse.getData().getSessionId());

                if (orderStatus == 2) {
                    // Transaction successful
                    return completeWithdrawal(transaction, responseData);
                } else if (orderStatus == 3) {
                    // Transaction failed
                    return handleFailedWithdrawal(transaction, "Payment gateway declined the transaction");
                } else {
                    // Still pending or unknown state
                    log.info("Transaction {} still has status {}, leaving as is", transactionId, orderStatus);
                    return transaction;
                }
            } else {
                log.error("Failed to check transaction status: {}", statusResponse.getRespMsg());
                return transaction;
            }
        } catch (Exception e) {
            log.error("Error checking withdrawal status: {}", e.getMessage(), e);
            return transaction;
        }
    }

    /**
     * Generate a unique order ID for withdrawal tracking
     */
    private String generateOrderId(Long userId) {
        return "WD-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}