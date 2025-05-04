//package com.papaymoni.middleware.service.impl;
//
//import com.papaymoni.middleware.dto.PalmpayPayinWebhookDto;
//import com.papaymoni.middleware.event.NotificationEvent;
//import com.papaymoni.middleware.event.PaymentProcessedEvent;
//import com.papaymoni.middleware.model.Transaction;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.model.VirtualAccount;
//import com.papaymoni.middleware.model.enums.TransactionStatus;
//import com.papaymoni.middleware.model.enums.TransactionType;
//import com.papaymoni.middleware.repository.TransactionRepository;
//import com.papaymoni.middleware.service.PalmpayWebhookService;
//import com.papaymoni.middleware.service.PaymentService;
//import com.papaymoni.middleware.service.VirtualAccountService;
//import com.papaymoni.middleware.util.EaseIdSignUtil;
//import com.papaymoni.middleware.util.PalmpayVerifySignUtil;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.Instant;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_EXCHANGE;
//import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_KEY;
//import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_EXCHANGE;
//import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_PROCESSED_KEY;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class PalmpayWebhookServiceImpl implements PalmpayWebhookService {
//
//    private final VirtualAccountService virtualAccountService;
//    private final PaymentService paymentService;
//    private final TransactionRepository transactionRepository;
//    private final RabbitTemplate rabbitTemplate;
//
//    @Autowired
//    PalmpayVerifySignUtil palmpayVerifySign;
//
//    @Value("${palmpay.gateway.public.key}")
//    private String palmpayPublicKey;
//
//    @Override
//    @Transactional
//    public boolean processPayinWebhook(PalmpayPayinWebhookDto webhookDto) {
//        log.info("Processing Palmpay pay-in webhook for order: {}", webhookDto.getOrderNo());
//
//        // Step 1: Verify the signature
//        if (!verifyWebhookSignature(webhookDto)) {
//            log.error("Invalid signature for webhook: {}", webhookDto.getOrderNo());
//            // For testing purposes, bypass signature verification
//            //return true;
//            return false;
//        }
//
//        // Step 2: Check if this is a valid status
//        // status 1 means successful deposit
//        if (webhookDto.getOrderStatus() != 1) {
//            log.warn("Webhook has non-success status {}: {}", webhookDto.getOrderStatus(), webhookDto.getOrderNo());
//            return false;
//        }
//
//        try {
//            // Step 3: Find the virtual account
//            VirtualAccount virtualAccount;
//            try {
//                virtualAccount = virtualAccountService.getVirtualAccountByAccountNumber(webhookDto.getVirtualAccountNo());
//            } catch (Exception e) {
//                log.error("Virtual account not found: {}", webhookDto.getVirtualAccountNo());
//                return false;
//            }
//
//            // Step 4: Check if the transaction already exists to prevent duplicates
//            Optional<Transaction> existingTransaction = transactionRepository.findByExternalReference(webhookDto.getOrderNo());
//            if (existingTransaction.isPresent()) {
//                log.warn("Transaction already exists for order: {}", webhookDto.getOrderNo());
//                return true; // Already processed, consider it successful
//            }
//
//            // Step 5: Get the amount in proper decimal format (convert from cents)
//            BigDecimal amount = BigDecimal.valueOf(webhookDto.getOrderAmount()).divide(BigDecimal.valueOf(100));
//
//            // Step 6: Process the deposit through the payment service
//            User user = virtualAccount.getUser();
//            Transaction transaction = processDeposit(virtualAccount, amount, webhookDto);
//
//            // Step 7: Publish payment processed event
//            publishPaymentEvent(transaction, user);
//
//            // Step 8: Send notification to user about the deposit
//            sendDepositNotification(user, amount, webhookDto.getCurrency(), transaction.getId());
//
//            log.info("Successfully processed pay-in webhook: {}, amount: {}",
//                    webhookDto.getOrderNo(), amount);
//            return true;
//
//        } catch (Exception e) {
//            log.error("Error processing pay-in webhook: {}", e.getMessage(), e);
//            return false;
//        }
//    }
//
//    @Override
//    public boolean verifyWebhookSignature(PalmpayPayinWebhookDto webhookDto) {
//        try {
//            // Skip signature verification if the sign is missing or empty
//            if (webhookDto.getSign() == null || webhookDto.getSign().isEmpty()) {
//                log.warn("Missing signature in webhook payload");
//                return false;
//            }
//
//            // Create a map of parameters to verify
//            Map<String, Object> params = new HashMap<>();
//            params.put("orderNo", webhookDto.getOrderNo());
//            params.put("orderStatus", webhookDto.getOrderStatus());
//            params.put("createdTime", webhookDto.getCreatedTime());
//            params.put("updateTime", webhookDto.getUpdateTime());
//            params.put("currency", webhookDto.getCurrency());
//            params.put("orderAmount", webhookDto.getOrderAmount());
//            params.put("payerAccountNo", webhookDto.getPayerAccountNo());
//            params.put("payerAccountName", webhookDto.getPayerAccountName());
//            params.put("payerBankName", webhookDto.getPayerBankName());
//
//            // Add optional parameters if present
//            if (webhookDto.getReference() != null && !webhookDto.getReference().isEmpty()) {
//                params.put("reference", webhookDto.getReference());
//            }
//            if (webhookDto.getVirtualAccountNo() != null && !webhookDto.getVirtualAccountNo().isEmpty()) {
//                params.put("virtualAccountNo", webhookDto.getVirtualAccountNo());
//            }
//            if (webhookDto.getVirtualAccountName() != null && !webhookDto.getVirtualAccountName().isEmpty()) {
//                params.put("virtualAccountName", webhookDto.getVirtualAccountName());
//            }
//            if (webhookDto.getAccountReference() != null && !webhookDto.getAccountReference().isEmpty()) {
//                params.put("accountReference", webhookDto.getAccountReference());
//            }
//            if (webhookDto.getSessionId() != null && !webhookDto.getSessionId().isEmpty()) {
//                params.put("sessionId", webhookDto.getSessionId());
//            }
//
//            // Verify the signature using the EaseIdSignUtil
//            return EaseIdSignUtil.verifySign(params, palmpayPublicKey, webhookDto.getSign(),
//                    EaseIdSignUtil.SignType.RSA);
//
//        } catch (Exception e) {
//            log.error("Error verifying webhook signature: {}", e.getMessage(), e);
//            return false;
//        }
//    }
//
//    /**
//     * Process deposit to the virtual account
//     */
//    private Transaction processDeposit(VirtualAccount virtualAccount, BigDecimal amount, PalmpayPayinWebhookDto webhookDto) {
//        Transaction transaction = new Transaction();
//        transaction.setUser(virtualAccount.getUser());
//        transaction.setVirtualAccount(virtualAccount);
//        transaction.setTransactionType(TransactionType.DEPOSIT.name());
//        transaction.setAmount(amount);
//        transaction.setFee(BigDecimal.ZERO); // No fee for deposits
//        transaction.setCurrency(webhookDto.getCurrency());
//        transaction.setStatus(TransactionStatus.COMPLETED.name());
//        transaction.setExternalReference(webhookDto.getOrderNo());
//
//        // Set payment details for reference
//        transaction.setPaymentMethod("BANK_TRANSFER");
//        transaction.setPaymentDetails(String.format("%s - %s - %s",
//                webhookDto.getPayerBankName(),
//                webhookDto.getPayerAccountNo(),
//                webhookDto.getPayerAccountName()));
//
//        // Convert unix timestamp to LocalDateTime if needed
//        if (webhookDto.getCreatedTime() != null) {
//            transaction.setCreatedAt(
//                    LocalDateTime.ofInstant(
//                            Instant.ofEpochMilli(webhookDto.getCreatedTime()),
//                            ZoneId.systemDefault()
//                    )
//            );
//        } else {
//            transaction.setCreatedAt(LocalDateTime.now());
//        }
//
//        transaction.setCompletedAt(LocalDateTime.now());
//
//        // Save the transaction
//        Transaction savedTransaction = transactionRepository.save(transaction);
//
//        // Update the virtual account balance
//        BigDecimal newBalance = virtualAccount.getBalance().add(amount);
//        virtualAccountService.updateAccountBalance(virtualAccount, newBalance);
//
//        log.info("Created deposit transaction {} for user {} with amount {}",
//                savedTransaction.getId(), virtualAccount.getUser().getId(), amount);
//
//        return savedTransaction;
//    }
//
//    /**
//     * Publish payment processed event to RabbitMQ
//     */
//    private void publishPaymentEvent(Transaction transaction, User user) {
//        PaymentProcessedEvent event = new PaymentProcessedEvent(
//                transaction.getId(), user.getId(), null);
//
//        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY, event);
//        log.info("Published PaymentProcessedEvent for transaction {}", transaction.getId());
//    }
//
//    /**
//     * Send notification to user about the deposit
//     */
//    private void sendDepositNotification(User user, BigDecimal amount, String currency, Long transactionId) {
//        String title = "Deposit Received";
//        String message = String.format("Your account has been credited with %s %s. Transaction ID: %s",
//                amount, currency, transactionId);
//
//        NotificationEvent notificationEvent = new NotificationEvent(
//                user.getId(), "EMAIL", title, message);
//
//        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY, notificationEvent);
//        log.info("Sent deposit notification to user {}", user.getId());
//    }
//}



//package com.papaymoni.middleware.service.impl;
//
//import com.fasterxml.jackson.annotation.JsonInclude;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.papaymoni.middleware.dto.PalmpayPayinWebhookDto;
//import com.papaymoni.middleware.event.NotificationEvent;
//import com.papaymoni.middleware.event.PaymentProcessedEvent;
//import com.papaymoni.middleware.model.Transaction;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.model.VirtualAccount;
//import com.papaymoni.middleware.model.enums.TransactionStatus;
//import com.papaymoni.middleware.model.enums.TransactionType;
//import com.papaymoni.middleware.repository.TransactionRepository;
//import com.papaymoni.middleware.service.PalmpayWebhookService;
//import com.papaymoni.middleware.service.PaymentService;
//import com.papaymoni.middleware.service.VirtualAccountService;
//import com.papaymoni.middleware.util.EaseIdSignUtil;
//import com.papaymoni.middleware.util.PalmpayVerifySignUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.Instant;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_EXCHANGE;
//import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_KEY;
//import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_EXCHANGE;
//import static com.papaymoni.middleware.config.RabbitMQConfig.PAYMENT_PROCESSED_KEY;
//
//@Slf4j
//@Service
//public class PalmpayWebhookServiceImpl implements PalmpayWebhookService {
//
//    private final VirtualAccountService virtualAccountService;
//    private final PaymentService paymentService;
//    private final TransactionRepository transactionRepository;
//    private final RabbitTemplate rabbitTemplate;
//    private final PalmpayVerifySignUtil palmpayVerifySignUtil;
//    private final ObjectMapper objectMapper;
//
//    @Value("${palmpay.gateway.public.key}")
//    private String palmpayPublicKey;
//
//    @Value("${palmpay.webhook.verify.signatures:true}")
//    private boolean verifySignatures;
//
//    public PalmpayWebhookServiceImpl(
//            VirtualAccountService virtualAccountService,
//            PaymentService paymentService,
//            TransactionRepository transactionRepository,
//            RabbitTemplate rabbitTemplate,
//            PalmpayVerifySignUtil palmpayVerifySignUtil,
//            ObjectMapper objectMapper) {
//        this.virtualAccountService = virtualAccountService;
//        this.paymentService = paymentService;
//        this.transactionRepository = transactionRepository;
//        this.rabbitTemplate = rabbitTemplate;
//        this.palmpayVerifySignUtil = palmpayVerifySignUtil;
//        this.objectMapper = objectMapper;
//    }
//
//    @Override
//    @Transactional
//    public boolean processPayinWebhook(PalmpayPayinWebhookDto webhookDto) {
//        log.info("Processing Palmpay pay-in webhook for order: {}", webhookDto.getOrderNo());
//
//        // Step 1: Verify the signature if verification is enabled
//        if (verifySignatures && !verifyWebhookSignature(webhookDto)) {
//            log.error("Invalid signature for webhook: {}", webhookDto.getOrderNo());
//            return false;
//        }
//
//        // Step 2: Check if this is a valid status (1 means successful deposit)
//        if (webhookDto.getOrderStatus() != 1) {
//            log.warn("Webhook has non-success status {}: {}", webhookDto.getOrderStatus(), webhookDto.getOrderNo());
//            return false;
//        }
//
//        try {
//            // Step 3: Find the virtual account
//            VirtualAccount virtualAccount;
//            try {
//                virtualAccount = virtualAccountService.getVirtualAccountByAccountNumber(webhookDto.getVirtualAccountNo());
//            } catch (Exception e) {
//                log.error("Virtual account not found: {}", webhookDto.getVirtualAccountNo());
//                return false;
//            }
//
//            // Step 4: Check if the transaction already exists to prevent duplicates
//            Optional<Transaction> existingTransaction = transactionRepository.findByExternalReference(webhookDto.getOrderNo());
//            if (existingTransaction.isPresent()) {
//                log.warn("Transaction already exists for order: {}", webhookDto.getOrderNo());
//                return true; // Already processed, consider it successful
//            }
//
//            // Step 5: Get the amount in proper decimal format (convert from cents)
//            BigDecimal amount = BigDecimal.valueOf(webhookDto.getOrderAmount()).divide(BigDecimal.valueOf(100));
//
//            // Step 6: Create payer details map
//            Map<String, String> payerDetails = new HashMap<>();
//            payerDetails.put("bankName", webhookDto.getPayerBankName());
//            payerDetails.put("accountNo", webhookDto.getPayerAccountNo());
//            payerDetails.put("accountName", webhookDto.getPayerAccountName());
//
//            // Step 7: Process the deposit through the payment service
//            User user = virtualAccount.getUser();
//            Transaction transaction = paymentService.processPalmpayDeposit(
//                    virtualAccount, amount, webhookDto.getOrderNo(), payerDetails);
//
//            // Step 8: Publish payment processed event
//            publishPaymentEvent(transaction, user);
//
//            // Step 9: Send notification to user about the deposit
//            sendDepositNotification(user, amount, webhookDto.getCurrency(), transaction.getId());
//
//            log.info("Successfully processed pay-in webhook: {}, amount: {}",
//                    webhookDto.getOrderNo(), amount);
//            return true;
//
//        } catch (Exception e) {
//            log.error("Error processing pay-in webhook: {}", e.getMessage(), e);
//            return false;
//        }
//    }
//
//    @Override
//    public boolean verifyWebhookSignature(PalmpayPayinWebhookDto webhookDto) {
//        try {
//            // Skip signature verification if the sign is missing or empty
//            if (webhookDto.getSign() == null || webhookDto.getSign().isEmpty()) {
//                log.warn("Missing signature in webhook payload");
//                return false;
//            }
//
//            // Convert the DTO to a JSON string
//            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//            String jsonPayload = objectMapper.writeValueAsString(webhookDto);
//          //  objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//
//            log.info("JsonPayload: {}", jsonPayload);
//            // Use the PalmpayVerifySignUtil to verify the signature
//            Boolean signVerificationStatus = palmpayVerifySignUtil.verifySignForCallback(
//                    jsonPayload, palmpayPublicKey, EaseIdSignUtil.SignType.RSA);
//            log.info("signVerificationStatus: {}", signVerificationStatus);
//            return signVerificationStatus;
//
//        } catch (Exception e) {
//            log.error("Error verifying webhook signature: {}", e.getMessage(), e);
//            return false;
//        }
//    }
//
//    /**
//     * Publish payment processed event to RabbitMQ
//     */
//    private void publishPaymentEvent(Transaction transaction, User user) {
//        PaymentProcessedEvent event = new PaymentProcessedEvent(
//                transaction.getId(), user.getId(), null);
//
//        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_PROCESSED_KEY, event);
//        log.info("Published PaymentProcessedEvent for transaction {}", transaction.getId());
//    }
//
//    /**
//     * Send notification to user about the deposit
//     */
//    private void sendDepositNotification(User user, BigDecimal amount, String currency, Long transactionId) {
//        String title = "Deposit Received";
//        String message = String.format("Your account has been credited with %s %s. Transaction ID: %s",
//                amount, currency, transactionId);
//
//        NotificationEvent notificationEvent = new NotificationEvent(
//                user.getId(), "EMAIL", title, message);
//
//        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY, notificationEvent);
//        log.info("Sent deposit notification to user {}", user.getId());
//    }
//}

package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.PalmpayPayinWebhookDto;
import com.papaymoni.middleware.event.NotificationEvent;
import com.papaymoni.middleware.event.PaymentProcessedEvent;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.PalmpayWebhookService;
import com.papaymoni.middleware.service.PaymentService;
import com.papaymoni.middleware.service.VirtualAccountService;
import com.papaymoni.middleware.util.EaseIdSignUtil;
import com.papaymoni.middleware.util.PalmpayVerifySignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_EXCHANGE;
import static com.papaymoni.middleware.config.RabbitMQConfig.NOTIFICATION_KEY;

@Slf4j
@Service
public class PalmpayWebhookServiceImpl implements PalmpayWebhookService {

    private final VirtualAccountService virtualAccountService;
    private final PaymentService paymentService;
    private final TransactionRepository transactionRepository;
    private final RabbitTemplate rabbitTemplate;
    private final PalmpayVerifySignUtil palmpayVerifySignUtil;
    private final ObjectMapper objectMapper;

    @Value("${palmpay.gateway.public.key}")
    private String palmpayPublicKey;

    @Value("${palmpay.webhook.verify.signatures:true}")
    private boolean verifySignatures;

    public PalmpayWebhookServiceImpl(
            VirtualAccountService virtualAccountService,
            PaymentService paymentService,
            TransactionRepository transactionRepository,
            RabbitTemplate rabbitTemplate,
            PalmpayVerifySignUtil palmpayVerifySignUtil,
            ObjectMapper objectMapper) {
        this.virtualAccountService = virtualAccountService;
        this.paymentService = paymentService;
        this.transactionRepository = transactionRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.palmpayVerifySignUtil = palmpayVerifySignUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public boolean processPayinWebhook(PalmpayPayinWebhookDto webhookDto) {
        log.info("Processing Palmpay pay-in webhook for order: {}", webhookDto.getOrderNo());

        // Step 1: Verify the signature if verification is enabled
        if (verifySignatures && !verifyWebhookSignature(webhookDto)) {
            log.error("Invalid signature for webhook: {}", webhookDto.getOrderNo());
            return false;
        }

        // Step 2: Check if this is a valid status (1 means successful deposit)
        if (webhookDto.getOrderStatus() != 1) {
            log.warn("Webhook has non-success status {}: {}", webhookDto.getOrderStatus(), webhookDto.getOrderNo());
            return false;
        }

        try {
            // Step 3: Find the virtual account
            VirtualAccount virtualAccount;
            try {
                virtualAccount = virtualAccountService.getVirtualAccountByAccountNumber(webhookDto.getVirtualAccountNo());
            } catch (Exception e) {
                log.error("Virtual account not found: {}", webhookDto.getVirtualAccountNo());
                return false;
            }

            // Step 4: Check if the transaction already exists to prevent duplicates
            Optional<Transaction> existingTransaction = transactionRepository.findByExternalReference(webhookDto.getOrderNo());
            if (existingTransaction.isPresent()) {
                log.warn("Transaction already exists for order: {}", webhookDto.getOrderNo());
                return true; // Already processed, consider it successful
            }

            // Step 5: Get the amount in proper decimal format (convert from cents)
            BigDecimal amount = BigDecimal.valueOf(webhookDto.getOrderAmount()).divide(BigDecimal.valueOf(100));

            // Step 6: Validate currency matches virtual account currency
            if (!virtualAccount.getCurrency().equalsIgnoreCase(webhookDto.getCurrency())) {
                log.error("Currency mismatch: virtual account expects {}, but webhook has {}",
                        virtualAccount.getCurrency(), webhookDto.getCurrency());
                return false;
            }

            // Step 7: Create payer details map
            Map<String, String> payerDetails = new HashMap<>();
            payerDetails.put("bankName", webhookDto.getPayerBankName());
            payerDetails.put("accountNo", webhookDto.getPayerAccountNo());
            payerDetails.put("accountName", webhookDto.getPayerAccountName());

            // Add reference information if available
            if (webhookDto.getReference() != null && !webhookDto.getReference().isEmpty()) {
                payerDetails.put("reference", webhookDto.getReference());
            }

            // Add session ID if available
            if (webhookDto.getSessionId() != null && !webhookDto.getSessionId().isEmpty()) {
                payerDetails.put("sessionId", webhookDto.getSessionId());
            }

            // Step 8: Process the deposit through the payment service
            User user = virtualAccount.getUser();
            Transaction transaction = paymentService.processPalmpayDeposit(
                    virtualAccount, amount, webhookDto.getOrderNo(), payerDetails);

            // Step 9: Send notification to user about the deposit
            sendDepositNotification(user, amount, webhookDto.getCurrency(), transaction.getId());

            log.info("Successfully processed pay-in webhook: {}, amount: {} {}",
                    webhookDto.getOrderNo(), amount, webhookDto.getCurrency());
            return true;

        } catch (Exception e) {
            log.error("Error processing pay-in webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(PalmpayPayinWebhookDto webhookDto) {
        try {
            // Skip signature verification if the sign is missing or empty
            if (webhookDto.getSign() == null || webhookDto.getSign().isEmpty()) {
                log.warn("Missing signature in webhook payload");
                return false;
            }

            // Convert the DTO to a JSON string
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            String jsonPayload = objectMapper.writeValueAsString(webhookDto);

            log.debug("JsonPayload for verification: {}", jsonPayload);

            // Use the PalmpayVerifySignUtil to verify the signature
            Boolean signVerificationStatus = palmpayVerifySignUtil.verifySignForCallback(
                    jsonPayload, palmpayPublicKey, EaseIdSignUtil.SignType.RSA);

            log.info("Signature verification status: {}", signVerificationStatus);
            return signVerificationStatus;

        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send notification to user about the deposit
     */
    private void sendDepositNotification(User user, BigDecimal amount, String currency, Long transactionId) {
        String title = "Deposit Received - Transaction #" + transactionId;
        String message = String.format(
                "Dear %s,\n\n" +
                        "Your deposit has been successfully processed:\n\n" +
                        "Amount: %s %s\n" +
                        "Transaction ID: %s\n" +
                        "Status: Completed\n\n" +
                        "The funds have been credited to your %s wallet.\n\n" +
                        "Best regards,\n" +
                        "Papaymoni Team",
                user.getFirstName(),
                amount.setScale(2, RoundingMode.HALF_UP),
                currency,
                transactionId,
                currency
        );

        NotificationEvent notificationEvent = new NotificationEvent(
                user.getId(), "EMAIL", title, message);

        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_KEY, notificationEvent);
        log.info("Sent deposit notification to user {}", user.getId());
    }
}