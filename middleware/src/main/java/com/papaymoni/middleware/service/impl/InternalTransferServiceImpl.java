package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.InternalTransferRequestDto;
import com.papaymoni.middleware.dto.InternalTransferResponseDto;
import com.papaymoni.middleware.dto.UsernameValidationDto;
import com.papaymoni.middleware.event.PaymentProcessedEvent;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.papaymoni.middleware.config.RabbitMQConfig.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferServiceImpl implements InternalTransferService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final WalletBalanceService walletBalanceService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final CashbackService cashbackService;
    private final VirtualAccountService virtualAccountService;
    private final GLService glService;
    private final ReceiptService receiptService;

    @Override
    public UsernameValidationDto validateRecipientUsername(String username) {
        log.info("Validating recipient username: {}", username);

        try {
            User user = userRepository.findByUsername(username)
                    .orElse(null);

            if (user == null) {
                return new UsernameValidationDto(false, null, "Username not found");
            }

            String fullName = user.getFirstName() + " " + user.getLastName();
            return new UsernameValidationDto(true, fullName, "Username validated successfully");

        } catch (Exception e) {
            log.error("Error validating username: {}", username, e);
            return new UsernameValidationDto(false, null, "Error validating username");
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public InternalTransferResponseDto processInternalTransfer(User sender, InternalTransferRequestDto transferRequest) {
        log.info("Processing internal transfer from {} to {} for amount {} {}",
                sender.getUsername(), transferRequest.getRecipientUsername(),
                transferRequest.getAmount(), transferRequest.getCurrency());

        // Validate recipient exists
        User recipient = userRepository.findByUsername(transferRequest.getRecipientUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient username not found: " + transferRequest.getRecipientUsername()));

        // Prevent self-transfer
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }

        BigDecimal amount = transferRequest.getAmount();
        String currency = transferRequest.getCurrency();

        // Internal transfers have zero fee
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal totalAmount = amount; // No fee for internal transfers

        // Check sender balance
        if (!walletBalanceService.hasSufficientBalance(sender, currency, totalAmount)) {
            throw new InsufficientBalanceException("Insufficient balance for transfer");
        }
        try {
            // Create withdrawal transaction for sender (initially PENDING)
            Transaction senderTransaction = createTransaction(sender, "WITHDRAWAL", amount.negate(),
                    BigDecimal.ZERO, currency, "Internal transfer to " + recipient.getUsername(),
                    transferRequest.getDescription(), "PENDING");

            // Create deposit transaction for recipient (initially PENDING)
            Transaction recipientTransaction = createTransaction(recipient, "DEPOSIT", amount,
                    BigDecimal.ZERO, currency, "Internal transfer from " + sender.getUsername(),
                    transferRequest.getDescription(), "PENDING");

            // Save transactions first
            senderTransaction = transactionRepository.save(senderTransaction);
            recipientTransaction = transactionRepository.save(recipientTransaction);


            // Update transaction statuses to COMPLETED
            senderTransaction.setStatus("COMPLETED");
            senderTransaction.setCompletedAt(LocalDateTime.now());
            recipientTransaction.setStatus("COMPLETED");
            recipientTransaction.setCompletedAt(LocalDateTime.now());

            // Save updated transactions
            senderTransaction = transactionRepository.save(senderTransaction);
            recipientTransaction = transactionRepository.save(recipientTransaction);

            // Process cashback for sender (withdrawal transaction)
            try {
                cashbackService.processCashback(sender, amount, currency, "WITHDRAWAL");
            } catch (Exception e) {
                log.warn("Failed to process cashback for internal transfer: {}", e.getMessage());
            }

            // Send notifications
            sendTransferNotifications(sender, recipient, amount, currency, senderTransaction.getId());

            // Publish events
            publishTransferEvents(senderTransaction, recipientTransaction);

            // Prepare response
            InternalTransferResponseDto response = new InternalTransferResponseDto();
            response.setTransactionId(senderTransaction.getId());
            response.setSenderUsername(sender.getUsername());
            response.setRecipientUsername(recipient.getUsername());
            response.setRecipientFullName(recipient.getFirstName() + " " + recipient.getLastName());
            response.setAmount(amount);
            response.setCurrency(currency);
            response.setDescription(transferRequest.getDescription());
            response.setStatus("COMPLETED");
            response.setCreatedAt(senderTransaction.getCreatedAt());
            response.setFee(fee);

            log.info("Internal transfer completed successfully: transaction ID {}", senderTransaction.getId());
            return response;

        } catch (Exception e) {
            log.error("Error processing internal transfer", e);
            throw new RuntimeException("Failed to process internal transfer: " + e.getMessage(), e);
        }
    }

    private void processSenderTransfer(User sender, VirtualAccount senderVirtualAccount,
                                       BigDecimal totalAmount, String currency, Transaction senderTransaction) {
        // Debit from sender's wallet balance
        walletBalanceService.debitWallet(sender, currency, totalAmount);
        log.info("Debited {} {} from sender {} wallet", totalAmount, currency, sender.getId());

        // Update sender's virtual account balance
        BigDecimal newSenderBalance = senderVirtualAccount.getBalance().subtract(totalAmount);
        virtualAccountService.updateAccountBalance(senderVirtualAccount, newSenderBalance);
        log.info("Updated sender virtual account {} balance to {}", senderVirtualAccount.getAccountNumber(), newSenderBalance);

        // GL entries for sender - debit user account for the transfer amount
        glService.debitUserAccount(sender, totalAmount);
        log.info("GL: Debited {} {} from sender {} account", totalAmount, currency, sender.getId());
    }

    private void processRecipientTransfer(User recipient, VirtualAccount recipientVirtualAccount,
                                          BigDecimal amount, String currency, Transaction recipientTransaction) {
        // Credit to recipient's wallet balance
        walletBalanceService.creditWallet(recipient, currency, amount);
        log.info("Credited {} {} to recipient {} wallet", amount, currency, recipient.getId());

        // Update recipient's virtual account balance
        BigDecimal newRecipientBalance = recipientVirtualAccount.getBalance().add(amount);
        virtualAccountService.updateAccountBalance(recipientVirtualAccount, newRecipientBalance);
        log.info("Updated recipient virtual account {} balance to {}", recipientVirtualAccount.getAccountNumber(), newRecipientBalance);

        // GL entries for recipient - credit user account
        glService.creditUserAccount(recipient, amount);
        log.info("GL: Credited {} {} to recipient {} account", amount, currency, recipient.getId());
    }

    private BigDecimal getMaxFeeForCurrency(String currency) {
        switch (currency.toUpperCase()) {
            case "NGN": return new BigDecimal("500.00");
            case "USD": return new BigDecimal("2.00");
            case "EUR": return new BigDecimal("2.00");
            case "GBP": return new BigDecimal("2.00");
            case "USDT": return new BigDecimal("2.00");
            default: return new BigDecimal("500.00");
        }
    }

    private Transaction createTransaction(User user, String type, BigDecimal amount, BigDecimal fee,
                                          String currency, String reference, String description, String status) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setTransactionType(type);
        transaction.setAmount(amount.abs());
        transaction.setFee(fee.abs());
        transaction.setCurrency(currency);
        transaction.setStatus(status);
        transaction.setExternalReference(UUID.randomUUID().toString());
        transaction.setPaymentMethod("INTERNAL_TRANSFER");
        transaction.setPaymentDetails(reference);
        transaction.setCreatedAt(LocalDateTime.now());

        if (description != null && !description.trim().isEmpty()) {
            transaction.setPaymentDetails(transaction.getPaymentDetails() + " - " + description);
        }

        return transaction;
    }

    private void sendTransferNotifications(User sender, User recipient, BigDecimal amount,
                                           String currency, Long transactionId) {
        // Notify sender
        String senderTitle = "Transfer Sent - Transaction #" + transactionId;
        String senderMessage = String.format(
                "You have successfully sent %s %s to %s (%s)",
                amount, currency,
                recipient.getFirstName() + " " + recipient.getLastName(),
                recipient.getUsername()
        );
        notificationService.createNotification(sender, "EMAIL", senderTitle, senderMessage);

        // Notify recipient
        String recipientTitle = "Transfer Received - Transaction #" + transactionId;
        String recipientMessage = String.format(
                "You have received %s %s from %s (%s)",
                amount, currency,
                sender.getFirstName() + " " + sender.getLastName(),
                sender.getUsername()
        );
        notificationService.createNotification(recipient, "EMAIL", recipientTitle, recipientMessage);
    }

    private void publishTransferEvents(Transaction senderTransaction, Transaction recipientTransaction) {
        // Publish payment processed events
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(senderTransaction.getId(), senderTransaction.getUser().getId(), null));

        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY,
                new PaymentProcessedEvent(recipientTransaction.getId(), recipientTransaction.getUser().getId(), null));
    }
}