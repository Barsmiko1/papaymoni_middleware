

package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.model.PassimpayWallet;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.model.enums.TransactionStatus;
import com.papaymoni.middleware.model.enums.TransactionType;
import com.papaymoni.middleware.repository.PassimpayWalletRepository;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassimpayServiceImpl implements PassimpayService {

    private final PassimpayWalletRepository passimpayWalletRepository;
    private final WalletBalanceService walletBalanceService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final GLService glService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final VirtualAccountService virtualAccountService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionEmailService transactionEmailService;
    private final ReceiptService receiptService;

    @Value("${passimpay.api.url}")
    private String apiUrl;

    @Value("${passimpay.platform.id}")
    private String platformId;

    @Value("${passimpay.api.key}")
    private String apiKey;

    // Fee configuration for USD withdrawals
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.02"); // 2%
    private static final BigDecimal FEE_CAP_USD = new BigDecimal("100.00"); // $100 cap

    // Fee configuration for USD deposits
    private static final BigDecimal DEPOSIT_FEE_PERCENTAGE = new BigDecimal("0.01"); // 1%
    private static final BigDecimal DEPOSIT_NETWORK_FEE = new BigDecimal("1.60"); // $1.6 fixed network fee
    private static final BigDecimal DEPOSIT_FEE_CAP_USD = new BigDecimal("100.00"); // $100 cap
    private static final BigDecimal MINIMUM_DEPOSIT_AMOUNT = new BigDecimal("5.00"); // $5 minimum deposit

    // Track transactions that have already sent notifications to prevent duplicates
    private final Set<Long> notifiedTransactions = Collections.synchronizedSet(new HashSet<>());

    private static final Map<String, Integer> CURRENCY_PAYMENT_IDS = new HashMap<>();

    // Initialize currency payment IDs
    static {
        CURRENCY_PAYMENT_IDS.put("BTC_BEP20", 201);
        CURRENCY_PAYMENT_IDS.put("ETH_BEP20", 202);
        CURRENCY_PAYMENT_IDS.put("USDT_BEP20", 72);
        CURRENCY_PAYMENT_IDS.put("USDT_TRC20", 71);
    }

    // Concurrent lock map for thread safety
    private final ConcurrentHashMap<String, Lock> userCurrencyLocks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public PassimpayWallet getOrCreateWalletAddress(User user, String currencyCode) {
        String normalizedCurrencyCode = currencyCode.toUpperCase();
        Integer paymentId = CURRENCY_PAYMENT_IDS.get(normalizedCurrencyCode);
        if (paymentId == null) {
            throw new IllegalArgumentException("Unsupported currency: " + currencyCode);
        }

        // Create a unique lock key for this user and currency
        String lockKey = user.getId() + "_" + normalizedCurrencyCode;
        // Get or create a lock for this user-currency combination
        Lock userCurrencyLock = userCurrencyLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        // Acquire the lock to prevent concurrent creation of the same wallet
        userCurrencyLock.lock();
        try {
            // Check if wallet already exists
            return passimpayWalletRepository.findByUserAndCurrency(user, normalizedCurrencyCode)
                    .orElseGet(() -> createNewWalletAddress(user, normalizedCurrencyCode, paymentId));
        } finally {
            // Release the lock
            userCurrencyLock.unlock();
            // Clean up the lock if no longer needed (optional, can improve memory usage)
            if (userCurrencyLock.tryLock()) {
                try {
                    // We got the lock, which means no one else is using it
                    // Safe to remove if this was the last operation for this user-currency
                    userCurrencyLocks.remove(lockKey);
                } finally {
                    userCurrencyLock.unlock();
                }
            }
        }
    }

    @Override
    public List<PassimpayWallet> getAllWalletAddresses(User user) {
        return passimpayWalletRepository.findAllByUser(user);
    }

    @Override
    public PassimpayCurrenciesResponseDto getSupportedCurrencies() {
        try {
            String requestBody = "{}";
            HttpHeaders headers = createHeaders(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<PassimpayCurrenciesResponseDto> response = restTemplate.postForEntity(
                    apiUrl + "/v2/currencies", entity, PassimpayCurrenciesResponseDto.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("Error fetching supported currencies from Passimpay", e);
            return PassimpayCurrenciesResponseDto.builder().result(0).build();
        }
    }

    @Override
    @Transactional
    public ApiResponse processDepositWebhook(PassimpayWebhookDto webhookDto, String signature) {
        try {
            PassimpayWallet wallet = passimpayWalletRepository.findByAddress(webhookDto.getAddressTo()).orElse(null);
            if (wallet == null) {
                log.error("Wallet not found for address: {}", webhookDto.getAddressTo());
                return ApiResponse.error("Wallet not found");
            }

            // Check if transaction already exists to prevent duplicates
            if (transactionService.existsByExternalReference(webhookDto.getTxhash())) {
                log.info("Transaction already processed: {}", webhookDto.getTxhash());
                return ApiResponse.success("Transaction already processed");
            }

            BigDecimal amount = new BigDecimal(webhookDto.getAmount());
            User user = wallet.getUser();

            // Check if deposit meets minimum requirement
            boolean isBelowMinimum = amount.compareTo(MINIMUM_DEPOSIT_AMOUNT) < 0;

            if (isBelowMinimum) {
                // Handle below minimum deposit
                log.warn("Deposit below minimum amount: {} USD (minimum: {} USD)",
                        amount, MINIMUM_DEPOSIT_AMOUNT);

                // Create transaction record for below minimum deposit
                Transaction transaction = new Transaction();
                transaction.setUser(user);
                transaction.setAmount(amount);
                transaction.setFee(BigDecimal.ZERO); // No fee charged for below minimum deposits
                transaction.setCurrency(wallet.getCurrency());
                transaction.setTransactionType(TransactionType.DEPOSIT.toString());
                transaction.setStatus(TransactionStatus.BELOW_MINIMUM.toString());
                transaction.setExternalReference(webhookDto.getTxhash());
                transaction.setPaymentDetails(String.format(
                        "Deposit via Passimpay - %s | Amount: %s USD | Below minimum deposit of %s USD",
                        wallet.getCurrency(),
                        amount.setScale(2, RoundingMode.HALF_UP),
                        MINIMUM_DEPOSIT_AMOUNT.setScale(2, RoundingMode.HALF_UP)
                ));
                transaction.setCreatedAt(LocalDateTime.now());
                transaction.setUpdatedAt(LocalDateTime.now());
                transaction.setCompletedAt(LocalDateTime.now());

                transaction = transactionService.save(transaction);

                // Send notification about below minimum deposit
                sendBelowMinimumDepositNotification(user, amount, wallet.getCurrency(), transaction.getId());

                log.info("Processed below minimum deposit for user: {}, amount: {}, currency: {}, txhash: {}",
                        user.getUsername(), amount, wallet.getCurrency(), webhookDto.getTxhash());

                return ApiResponse.success("Below minimum deposit recorded");
            }

            // Process normal deposit (above minimum)
            // Calculate deposit fee (1% + $1.6 network fee, capped at $100)
            BigDecimal fee = calculateDepositFee(amount);
            BigDecimal netAmount = amount.subtract(fee);

            log.info("Deposit fee calculated: {} USD ({}% of {} plus $1.6 network fee)",
                    fee, DEPOSIT_FEE_PERCENTAGE.multiply(new BigDecimal("100")), amount);
            log.info("Net amount to be credited: {} USD", netAmount);

            // Create transaction record with fee
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setAmount(amount); // Gross amount
            transaction.setFee(fee); // Fee amount
            transaction.setCurrency(wallet.getCurrency());
            transaction.setTransactionType(TransactionType.DEPOSIT.toString());
            transaction.setStatus(TransactionStatus.COMPLETED.toString());
            transaction.setExternalReference(webhookDto.getTxhash());
            transaction.setPaymentDetails(String.format(
                    "Deposit via Passimpay - %s | Gross Amount: %s USD | Fee (1%% + $1.6 network fee): %s USD | Net Amount: %s USD",
                    wallet.getCurrency(),
                    amount.setScale(2, RoundingMode.HALF_UP),
                    fee.setScale(2, RoundingMode.HALF_UP),
                    netAmount.setScale(2, RoundingMode.HALF_UP)
            ));
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            transaction.setCompletedAt(LocalDateTime.now());

            transaction = transactionService.save(transaction);

            // Credit user's wallet balance with net amount (after fee)
            walletBalanceService.creditWallet(user, "USD", netAmount);
            log.info("Credited {} USD to user {} wallet (after {} USD fee)",
                    netAmount, user.getId(), fee);

            // Credit GL for audit trail - net amount to user
            glService.creditUserAccount(user, netAmount);

            // Credit fee to platform account
            glService.creditFeeAccount(fee);
            log.info("Credited {} USD fee to platform account", fee);

            // Generate receipt for the transaction
            try {
                String receiptUrl = receiptService.generateReceipt(transaction);
                if (receiptUrl != null) {
                    transaction.setReceiptUrl(receiptUrl);
                    transaction = transactionRepository.save(transaction);
                    log.info("Generated receipt for deposit transaction {}: {}", transaction.getId(), receiptUrl);
                }
            } catch (Exception e) {
                log.error("Failed to generate receipt for deposit transaction {}: {}", transaction.getId(), e.getMessage());
            }

            // Send notification to user about the deposit with fee information
            sendDepositNotificationWithFee(user, amount, netAmount, fee, wallet.getCurrency(), transaction.getId());

            log.info("Processed webhook for user: {}, gross amount: {}, net amount: {}, fee: {}, currency: {}, txhash: {}",
                    user.getUsername(), amount, netAmount, fee, wallet.getCurrency(), webhookDto.getTxhash());

            return ApiResponse.success("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error processing deposit webhook", e);
            return ApiResponse.error("Error processing webhook: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse processWebhook(PassimpayWebhookDto webhookDto, String signature) {
        try {
            // Log the raw webhook payload
            String rawJsonBody = objectMapper.writeValueAsString(webhookDto);
            log.info("Raw webhook payload: {}", rawJsonBody);

            // Skip signature verification if the signature is missing or empty
            if (signature == null || signature.trim().isEmpty()) {
                log.warn("Signature is missing or empty, skipping verification");
                return processDepositWebhook(webhookDto, signature);
            }

            // Verify the signature
            boolean isValid = verifyWebhookSignature(rawJsonBody, signature);
            if (!isValid) {
                log.error("Invalid webhook signature");
                return ApiResponse.error("Invalid signature");
            }

            // If signature is valid, process the webhook
            return processDepositWebhook(webhookDto, signature);
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ApiResponse.error("Error processing webhook: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawJsonBody, String receivedSignature) {
        try {
            log.info("Starting signature verification");
            log.info("Raw JSON body: {}", rawJsonBody);

            // Extract platformId directly from the JSON
            Map<String, Object> bodyMap = objectMapper.readValue(rawJsonBody, new TypeReference<Map<String, Object>>() {
            });
            Object platformIdObj = bodyMap.get("platformId");
            if (platformIdObj == null) {
                log.warn("platformId missing in payload");
                return false;
            }

            // Construct signature contract string exactly as in the Passimpay example
            String signatureContract = platformIdObj + ";" + rawJsonBody + ";" + apiKey;
            log.info("Signature contract (without API key): {}; {}", platformIdObj, rawJsonBody);

            // Generate HMAC-SHA256 signature
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);

            byte[] hash = sha256HMAC.doFinal(signatureContract.getBytes(StandardCharsets.UTF_8));
            StringBuilder expectedSignature = new StringBuilder();
            for (byte b : hash) {
                expectedSignature.append(String.format("%02x", b));
            }

            log.info("Expected signature: {}", expectedSignature);
            log.info("Received signature: {}", receivedSignature);

            boolean matches = expectedSignature.toString().equalsIgnoreCase(receivedSignature);
            log.info("Signature verification result: {}", matches ? "VALID" : "INVALID");

            return matches;
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Calculate platform fee for USD deposits
     *
     * @param amount The deposit amount
     * @return The calculated fee
     */
    private BigDecimal calculateDepositFee(BigDecimal amount) {
        // Calculate percentage fee (1%)
        BigDecimal percentageFee = amount.multiply(DEPOSIT_FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);

        // Add fixed network fee ($1.6)
        BigDecimal totalFee = percentageFee.add(DEPOSIT_NETWORK_FEE).setScale(2, RoundingMode.HALF_UP);

        // Apply fee cap
        if (totalFee.compareTo(DEPOSIT_FEE_CAP_USD) > 0) {
            totalFee = DEPOSIT_FEE_CAP_USD;
        }

        return totalFee;
    }

    /**
     * Send notification for deposit with fee information
     */
    private void sendDepositNotificationWithFee(User user, BigDecimal grossAmount, BigDecimal netAmount,
                                                BigDecimal fee, String currency, Long transactionId) {
        try {
            String title = "Crypto Deposit Received - Transaction #" + transactionId;
            String message = String.format(
                    "Dear %s,\n\n" +
                            "Your crypto deposit has been received and processed:\n\n" +
                            "Gross Amount: %s %s\n" +
                            "Fee (1%% + $1.6 network fee): %s %s\n" +
                            "Net Amount: %s %s\n" +
                            "Transaction ID: %s\n\n" +
                            "The net amount has been credited to your %s wallet.\n\n" +
                            "Best regards,\n" +
                            "Papaymoni Team",
                    user.getFirstName(),
                    grossAmount.setScale(2, RoundingMode.HALF_UP),
                    "USD",
                    fee.setScale(2, RoundingMode.HALF_UP),
                    "USD",
                    netAmount.setScale(2, RoundingMode.HALF_UP),
                    "USD",
                    transactionId,
                    "USD"
            );

            notificationService.createNotification(user, "EMAIL", title, message);
            log.info("Sent deposit notification with fee details to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send deposit notification with fee: {}", e.getMessage());
        }
    }

    /**
     * Send notification to user about below minimum deposit
     */
    private void sendBelowMinimumDepositNotification(User user, BigDecimal amount, String currency, Long transactionId) {
        try {
            String title = "Deposit Below Minimum Amount - Transaction #" + transactionId;
            String message = String.format(
                    "Dear %s,\n\n" +
                            "We have received your crypto deposit, but we are unable to process it because it is below our minimum deposit requirement:\n\n" +
                            "Amount Received: %s %s\n" +
                            "Minimum Required: %s %s\n" +
                            "Transaction ID: %s\n\n" +
                            "Please note that deposits below the minimum amount cannot be processed automatically. " +
                            "Please contact our support team for assistance with this transaction.\n\n" +
                            "Best regards,\n" +
                            "Papaymoni Team",
                    user.getFirstName(),
                    amount.setScale(2, RoundingMode.HALF_UP),
                    "USD",
                    MINIMUM_DEPOSIT_AMOUNT.setScale(2, RoundingMode.HALF_UP),
                    "USD",
                    transactionId
            );

            // Create in-app notification
            notificationService.createNotification(user, "EMAIL", title, message);

            // Send email notification using existing email service
            notificationService.createNotification(user, "EMAIL", title, message);
            log.info("Sent below minimum deposit notification to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send below minimum deposit notification: {}", e.getMessage());
        }
    }

    @Override
    public PassimpayNetworkFeeResponseDto getNetworkFee(Integer paymentId, String walletAddress, BigDecimal amount) {
        try {
            log.info("Getting network fee for paymentId: {}, walletAddress: {}, amount: {}",
                    paymentId, walletAddress, amount);

            // Validate input parameters
            if (paymentId == null) {
                throw new IllegalArgumentException("Payment ID cannot be null");
            }
            if (walletAddress == null || walletAddress.trim().isEmpty()) {
                throw new IllegalArgumentException("Wallet address cannot be empty");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }

            // Log API credentials (without sensitive parts)
            log.debug("Using Passimpay API URL: {}, Platform ID: {}",
                    apiUrl, platformId);

            PassimpayNetworkFeeRequestDto requestDto = PassimpayNetworkFeeRequestDto.builder()
                    .platformId(Integer.parseInt(platformId))
                    .paymentId(paymentId)
                    .addressTo(walletAddress)
                    .amount(amount.toPlainString())
                    .build();

            String requestBody = objectMapper.writeValueAsString(requestDto);
            log.debug("Network fee request body: {}", requestBody);

            HttpHeaders headers;
            try {
                headers = createHeaders(requestBody);
            } catch (Exception e) {
                log.error("Failed to create request headers", e);
                throw new RuntimeException("Error creating request headers: " + e.getMessage());
            }

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            try {
                ResponseEntity<PassimpayNetworkFeeResponseDto> response = restTemplate.postForEntity(
                        apiUrl + "/v2/fees", entity, PassimpayNetworkFeeResponseDto.class);

                PassimpayNetworkFeeResponseDto responseDto = response.getBody();
                if (responseDto == null) {
                    throw new RuntimeException("Received null response from Passimpay API");
                }

                if (responseDto.getResult() != 1) {
                    log.error("Passimpay API returned error result: {}, message: {}",
                            responseDto.getResult(), responseDto.getMessage());
                    throw new RuntimeException("Passimpay API error: " +
                            (responseDto.getMessage() != null ? responseDto.getMessage() : "Unknown error"));
                }

                log.info("Successfully retrieved network fee: {}", responseDto);
                return responseDto;
            } catch (HttpClientErrorException e) {
                log.error("HTTP client error when calling Passimpay API: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                throw new RuntimeException("Passimpay API client error: " + e.getMessage() +
                        ", Status: " + e.getStatusCode() +
                        ", Response: " + e.getResponseBodyAsString());
            } catch (HttpServerErrorException e) {
                log.error("HTTP server error when calling Passimpay API: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                throw new RuntimeException("Passimpay API server error: " + e.getMessage() +
                        ", Status: " + e.getStatusCode() +
                        ", Response: " + e.getResponseBodyAsString());
            } catch (ResourceAccessException e) {
                log.error("Network error when calling Passimpay API", e);
                throw new RuntimeException("Network error connecting to Passimpay API: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error when calling Passimpay API", e);
                throw new RuntimeException("Error calling Passimpay API: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error getting network fee", e);
            throw new RuntimeException("Failed to get network fee: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Transaction> initiateWithdrawal(User user, PassimpayWithdrawalRequestDto withdrawalRequest) {
        log.info("Initiating Passimpay withdrawal for user: {}, amount: {} USD, to address: {}",
                user.getId(), withdrawalRequest.getAmount(), withdrawalRequest.getWalletAddress());

        try {
            // Validate the withdrawal amount
            BigDecimal amount = withdrawalRequest.getAmount();
            String currency = "USD";

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid withdrawal amount: {}", amount);
                return ApiResponse.error("Withdrawal amount must be greater than zero");
            }

            // Get network fee from Passimpay
            PassimpayNetworkFeeResponseDto networkFeeResponse;
            try {
                networkFeeResponse = getNetworkFee(
                        withdrawalRequest.getPaymentId(),
                        withdrawalRequest.getWalletAddress(),
                        amount
                );
            } catch (Exception e) {
                log.error("Failed to get network fee", e);
                return ApiResponse.error("Unable to calculate network fee: " + e.getMessage());
            }

            // Calculate platform fee (2% of amount)
            BigDecimal platformFee = amount.multiply(FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);

            // Get network fee from API response
            BigDecimal networkFee = new BigDecimal(networkFeeResponse.getFeeNetwork());
            BigDecimal serviceFee = new BigDecimal(networkFeeResponse.getFeeService());

            // Total fee is platform fee + network fee + service fee
            BigDecimal totalFees = platformFee.add(networkFee).add(serviceFee);

            // Apply fee cap of $100
            if (totalFees.compareTo(FEE_CAP_USD) > 0) {
                totalFees = FEE_CAP_USD;
            }

            BigDecimal totalAmount = amount.add(totalFees);

            log.info("Withdrawal amount: {}, platform fee (2%): {}, network fee: {}, service fee: {}, total fee: {}, total amount: {}",
                    amount, platformFee, networkFee, serviceFee, totalFees, totalAmount);

            // Check user balance
            if (!walletBalanceService.hasSufficientBalance(user, currency, totalAmount)) {
                log.warn("Insufficient balance for withdrawal: user {}, required: {} {}",
                        user.getId(), totalAmount, currency);
                return ApiResponse.error("Insufficient balance to complete this withdrawal");
            }

            // Generate unique order ID for tracking
            String orderId = generateOrderId(user.getId());

            // Create a pending transaction record
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setTransactionType("WITHDRAWAL");
            transaction.setAmount(amount);
            transaction.setFee(totalFees);
            transaction.setCurrency(currency);
            transaction.setStatus("PENDING");
            transaction.setExternalReference(orderId); // Will be updated with Passimpay transactionId
            transaction.setPaymentMethod("CRYPTO_TRANSFER");
            transaction.setPaymentDetails(String.format(
                    "Payment ID: %d, Address: %s | Fee breakdown: Platform fee (2%%): %s USD, Network fee: %s USD, Service fee: %s USD",
                    withdrawalRequest.getPaymentId(),
                    withdrawalRequest.getWalletAddress(),
                    platformFee.setScale(2, RoundingMode.HALF_UP),
                    networkFee.setScale(8, RoundingMode.HALF_UP),
                    serviceFee.setScale(8, RoundingMode.HALF_UP)
            ));
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());

            Transaction savedTransaction = transactionRepository.save(transaction);
            log.info("Created pending withdrawal transaction: {}", savedTransaction.getId());

            // Deduct from user's wallet balance
            walletBalanceService.debitWallet(user, currency, totalAmount);
            log.info("Debited {} {} from user {} wallet", totalAmount, currency, user.getId());

            // Also deduct from GL for audit trail
            glService.debitUserAccount(user, totalAmount);

            // Credit fee to platform account
            glService.creditFeeAccount(totalFees);
            log.info("Credited {} {} fee to platform account", totalFees, currency);

            // Initiate the withdrawal via Passimpay
            PassimpayWithdrawalResponseDto withdrawalResponse;
            try {
                withdrawalResponse = initiatePassimpayWithdrawal(
                        withdrawalRequest.getPaymentId(),
                        withdrawalRequest.getWalletAddress(),
                        withdrawalRequest.getDestinationTag(),
                        amount
                );
            } catch (Exception e) {
                // Handle API call failure
                log.error("Failed to initiate withdrawal via Passimpay API", e);

                // Refund user's wallet
                walletBalanceService.creditWallet(user, currency, totalAmount);

                // Update GL entries
                glService.creditUserAccount(user, totalAmount);
                glService.debitFeeAccount(totalFees);

                // Update transaction status
                savedTransaction.setStatus("FAILED");
                savedTransaction.setUpdatedAt(LocalDateTime.now());
                savedTransaction.setPaymentDetails(savedTransaction.getPaymentDetails() +
                        " | Error: " + e.getMessage());
                savedTransaction = transactionRepository.save(savedTransaction);

                return ApiResponse.error("Failed to initiate withdrawal: " + e.getMessage());
            }

            if (withdrawalResponse.getResult() == 1) {
                // Update transaction with Passimpay transactionId
                savedTransaction.setExternalReference(withdrawalResponse.getTransactionId());
                savedTransaction.setPaymentDetails(savedTransaction.getPaymentDetails() +
                        " | Passimpay TransactionId: " + withdrawalResponse.getTransactionId());
                savedTransaction.setStatus("PROCESSING");
                savedTransaction = transactionRepository.save(savedTransaction);

                return ApiResponse.success("Withdrawal initiated successfully", savedTransaction);
            } else {
                // Handle failed initiation
                log.error("Failed to initiate withdrawal: {}", withdrawalResponse.getMessage());

                // Refund user's wallet
                walletBalanceService.creditWallet(user, currency, totalAmount);

                // Update GL entries
                glService.creditUserAccount(user, totalAmount);
                glService.debitFeeAccount(totalFees);

                // Update transaction status
                savedTransaction.setStatus("FAILED");
                savedTransaction.setUpdatedAt(LocalDateTime.now());
                savedTransaction = transactionRepository.save(savedTransaction);

                return ApiResponse.error("Withdrawal failed: " + withdrawalResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing withdrawal: {}", e.getMessage(), e);
            return ApiResponse.error("An error occurred while processing your withdrawal: " + e.getMessage());
        }
    }

    @Override
    public PassimpayWithdrawalStatusResponseDto checkWithdrawalStatus(String transactionId) {
        try {
            log.info("Checking withdrawal status for transaction ID: {}", transactionId);

            if (transactionId == null || transactionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction ID cannot be empty");
            }

            PassimpayWithdrawalStatusRequestDto requestDto = PassimpayWithdrawalStatusRequestDto.builder()
                    .platformId(Integer.parseInt(platformId))
                    .transactionId(transactionId)
                    .build();

            String requestBody = objectMapper.writeValueAsString(requestDto);
            log.debug("Withdrawal status request body: {}", requestBody);

            HttpHeaders headers;
            try {
                headers = createHeaders(requestBody);
            } catch (Exception e) {
                log.error("Failed to create request headers", e);
                throw new RuntimeException("Error creating request headers: " + e.getMessage());
            }

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            try {
                ResponseEntity<PassimpayWithdrawalStatusResponseDto> response = restTemplate.postForEntity(
                        apiUrl + "/v2/withdrawstatus", entity, PassimpayWithdrawalStatusResponseDto.class);

                PassimpayWithdrawalStatusResponseDto responseDto = response.getBody();
                if (responseDto == null) {
                    throw new RuntimeException("Received null response from Passimpay API");
                }

                if (responseDto.getResult() != 1) {
                    log.error("Passimpay API returned error result: {}, message: {}",
                            responseDto.getResult(), responseDto.getMessage());
                    throw new RuntimeException("Passimpay API error: " +
                            (responseDto.getMessage() != null ? responseDto.getMessage() : "Unknown error"));
                }

                log.info("Successfully retrieved withdrawal status: {}", responseDto);
                return responseDto;
            } catch (HttpClientErrorException e) {
                log.error("HTTP client error when calling Passimpay API: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                throw new RuntimeException("Passimpay API client error: " + e.getMessage() +
                        ", Status: " + e.getStatusCode() +
                        ", Response: " + e.getResponseBodyAsString());
            } catch (HttpServerErrorException e) {
                log.error("HTTP server error when calling Passimpay API: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                throw new RuntimeException("Passimpay API server error: " + e.getMessage() +
                        ", Status: " + e.getStatusCode() +
                        ", Response: " + e.getResponseBodyAsString());
            } catch (ResourceAccessException e) {
                log.error("Network error when calling Passimpay API", e);
                throw new RuntimeException("Network error connecting to Passimpay API: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error when calling Passimpay API", e);
                throw new RuntimeException("Error calling Passimpay API: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error checking withdrawal status", e);
            throw new RuntimeException("Failed to check withdrawal status: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Transaction updateTransactionStatus(Transaction transaction) {
        if (!"PROCESSING".equals(transaction.getStatus()) ||
                transaction.getExternalReference() == null ||
                !transaction.getPaymentMethod().equals("CRYPTO_TRANSFER")) {
            // Only process crypto withdrawals that are in PROCESSING state
            return transaction;
        }

        try {
            String passimpayTransactionId = transaction.getExternalReference();
            PassimpayWithdrawalStatusResponseDto statusResponse = checkWithdrawalStatus(passimpayTransactionId);

            if (statusResponse.getApprove() == 1) {
                // Withdrawal successful - only update if not already completed
                if (!"COMPLETED".equals(transaction.getStatus())) {
                    transaction.setStatus("COMPLETED");
                    transaction.setCompletedAt(LocalDateTime.now());
                    transaction.setUpdatedAt(LocalDateTime.now());

                    // Handle payment details properly to avoid exceeding column size
                    String txhashInfo = "";
                    if (statusResponse.getTxhash() != null) {
                        txhashInfo = " | Txhash: " + statusResponse.getTxhash();
                    }

                    // Get current payment details
                    String currentDetails = transaction.getPaymentDetails();

                    // Check if payment details already contains txhash info
                    if (currentDetails != null && currentDetails.contains("Txhash:")) {
                        // Don't add duplicate txhash info
                        log.debug("Transaction {} already has txhash info: {}", transaction.getId(), currentDetails);
                    } else {
                        // Calculate new payment details, respecting column size limit
                        // Assuming your payment_details column is VARCHAR(255)
                        final int MAX_COLUMN_SIZE = 255;

                        String newDetails;
                        if (currentDetails == null || currentDetails.isEmpty()) {
                            newDetails = "Txhash: " + statusResponse.getTxhash();
                        } else {
                            newDetails = currentDetails + txhashInfo;
                        }

                        // If the new details would be too long, truncate appropriately
                        if (newDetails.length() > MAX_COLUMN_SIZE) {
                            // Keep the most important parts - beginning and txhash info
                            int availableSpace = MAX_COLUMN_SIZE - txhashInfo.length();
                            if (availableSpace > 20) { // Make sure we have at least some space for original details
                                newDetails = currentDetails.substring(0, availableSpace - 3) + "..." + txhashInfo;
                            } else {
                                // If truly not enough space, prioritize the txhash info
                                newDetails = "..." + txhashInfo;
                            }
                        }

                        transaction.setPaymentDetails(newDetails);
                        log.debug("Updated payment details for transaction {}: {}", transaction.getId(), newDetails);
                    }

                    // Generate receipt for the completed withdrawal
                    try {
                        String receiptUrl = receiptService.generateReceipt(transaction);
                        if (receiptUrl != null) {
                            transaction.setReceiptUrl(receiptUrl);
                            log.info("Generated receipt for withdrawal transaction {}: {}", transaction.getId(), receiptUrl);
                        }
                    } catch (Exception e) {
                        log.error("Failed to generate receipt for withdrawal transaction {}: {}", transaction.getId(), e.getMessage());
                    }

                    // Send notification to user only if we haven't already
                    if (!notifiedTransactions.contains(transaction.getId())) {
                        User user = transaction.getUser();
                        sendWithdrawalNotification(
                                user.getId(),
                                user.getEmail(),
                                transaction.getAmount(),
                                transaction.getCurrency(),
                                transaction.getId(),
                                transaction.getFee()
                        );
                        notifiedTransactions.add(transaction.getId());
                    }

                    log.info("Withdrawal transaction {} completed successfully", transaction.getId());

                    // Save the updated transaction
                    return transactionRepository.save(transaction);
                } else {
                    log.info("Transaction {} is already in COMPLETED state, skipping update", transaction.getId());
                    return transaction;
                }
            } else if (statusResponse.getApprove() == 2) {
                // Withdrawal failed - only update if not already failed
                if (!"FAILED".equals(transaction.getStatus())) {
                    transaction = handleFailedWithdrawal(transaction, "Withdrawal failed: " +
                            (statusResponse.getMessage() != null ? statusResponse.getMessage() : "Unknown error"));

                    log.error("Withdrawal transaction {} failed", transaction.getId());

                    // Save the updated transaction
                    return transactionRepository.save(transaction);
                } else {
                    log.info("Transaction {} is already in FAILED state, skipping update", transaction.getId());
                    return transaction;
                }
            } else {
                // Still pending, no action needed
                log.info("Withdrawal transaction {} is still pending", transaction.getId());
                return transaction;
            }
        } catch (Exception e) {
            log.error("Error updating transaction status: {}", e.getMessage(), e);
            return transaction;
        }
    }

    private void sendWithdrawalNotification(Long id, String email, BigDecimal amount, String currency, Long id1, BigDecimal fee) {
    }

    /**
     * Scheduled task to check and update pending withdrawal transactions
     * Runs every 3 minute
     */
    @Scheduled(fixedDelay = 180000)
    @Transactional(propagation = Propagation.REQUIRED)
    public void checkPendingWithdrawals() {
        log.info("Running scheduled check for pending crypto withdrawals");

        try {
            // Get transaction IDs instead of full entities
            List<Long> pendingTransactionIds = transactionRepository.findIdsByStatusAndPaymentMethod("PROCESSING", "CRYPTO_TRANSFER");

            log.info("Found {} pending crypto withdrawal transactions", pendingTransactionIds.size());

            if (pendingTransactionIds.isEmpty()) {
                return; // No pending transactions to process
            }

            for (Long transactionId : pendingTransactionIds) {
                try {
                    // Start a new transaction for each update to avoid session issues
                    updatePendingTransaction(transactionId);
                } catch (Exception e) {
                    log.error("Error updating transaction {}: {}", transactionId, e.getMessage(), e);
                    // Continue with the next transaction
                }
            }
        } catch (Exception e) {
            log.error("Error in scheduled withdrawal check: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePendingTransaction(Long transactionId) {
        // Get a fresh instance of the transaction within this transaction
        Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);

        if (!transactionOpt.isPresent()) {
            log.warn("Transaction {} no longer exists", transactionId);
            return;
        }

        Transaction transaction = transactionOpt.get();

        // Double-check that the transaction is still in PROCESSING state
        if (!"PROCESSING".equals(transaction.getStatus())) {
            log.info("Transaction {} is no longer in PROCESSING state, current state: {}",
                    transactionId, transaction.getStatus());
            return;
        }

        // Call the existing method to update the transaction status
        updateTransactionStatus(transaction);
    }

    // Helper method to create a new wallet address
    private PassimpayWallet createNewWalletAddress(User user, String currencyCode, Integer paymentId) {
        try {
            String orderId = "user-" + user.getId() + "-" + currencyCode.toLowerCase() + "-" + System.currentTimeMillis();

            PassimpayAddressRequestDto requestDto = PassimpayAddressRequestDto.builder()
                    .platformId(Integer.parseInt(platformId))
                    .paymentId(paymentId)
                    .orderId(orderId)
                    .build();

            String requestBody = objectMapper.writeValueAsString(requestDto);
            HttpHeaders headers = createHeaders(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<PassimpayAddressResponseDto> response = restTemplate.postForEntity(
                    apiUrl + "/v2/address", entity, PassimpayAddressResponseDto.class);

            PassimpayAddressResponseDto responseDto = response.getBody();

            if (responseDto == null || responseDto.getResult() != 1) {
                throw new RuntimeException("Failed to create wallet address: " +
                        (responseDto != null ? responseDto.getMessage() : "No response"));
            }

            PassimpayWallet wallet = PassimpayWallet.builder()
                    .user(user)
                    .paymentId(paymentId)
                    .currency(currencyCode)
                    .address(responseDto.getAddress())
                    .destinationTag(responseDto.getDestinationTag())
                    .orderId(orderId)
                    .build();

            return passimpayWalletRepository.save(wallet);
        } catch (Exception e) {
            log.error("Error creating wallet address", e);
            throw new RuntimeException("Failed to create wallet address: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders(String requestBody) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String signatureContract = platformId + ";" + requestBody + ";" + apiKey;

        // Log the signature contract for debugging (without the API key)
        log.debug("Signature contract (without API key): {}; {}", platformId, requestBody);

        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256HMAC.init(secretKey);

        byte[] hash = sha256HMAC.doFinal(signatureContract.getBytes(StandardCharsets.UTF_8));
        StringBuilder signature = new StringBuilder();
        for (byte b : hash) {
            signature.append(String.format("%02x", b));
        }

        log.debug("Generated signature: {}", signature);
        headers.set("x-signature", signature.toString());
        return headers;
    }

    /**
     * Initiate a withdrawal via Passimpay API
     */
    private PassimpayWithdrawalResponseDto initiatePassimpayWithdrawal(
            Integer paymentId, String walletAddress, String destinationTag, BigDecimal amount) throws JsonProcessingException {

        log.info("Initiating Passimpay withdrawal: paymentId={}, address={}, amount={}",
                paymentId, walletAddress, amount);

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("platformId", Integer.parseInt(platformId));
        requestMap.put("paymentId", paymentId);
        requestMap.put("addressTo", walletAddress);
        requestMap.put("amount", amount.toPlainString());

        // Add destination tag if provided (for XRP, etc.)
        if (destinationTag != null && !destinationTag.isEmpty()) {
            String addressWithTag = walletAddress + ":" + destinationTag;
            requestMap.put("addressTo", addressWithTag);
            log.info("Using address with destination tag: {}", addressWithTag);
        }

        String requestBody = objectMapper.writeValueAsString(requestMap);
        log.debug("Withdrawal request body: {}", requestBody);

        try {
            HttpHeaders headers = createHeaders(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<PassimpayWithdrawalResponseDto> response = restTemplate.postForEntity(
                    apiUrl + "/v2/withdraw", entity, PassimpayWithdrawalResponseDto.class);

            PassimpayWithdrawalResponseDto responseDto = response.getBody();
            if (responseDto == null) {
                throw new RuntimeException("Received null response from Passimpay API");
            }

            log.info("Withdrawal API response: {}", responseDto);
            return responseDto;
        } catch (HttpClientErrorException e) {
            log.error("HTTP client error when calling Passimpay API: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Passimpay API client error: " + e.getMessage() +
                    ", Status: " + e.getStatusCode() +
                    ", Response: " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.error("HTTP server error when calling Passimpay API: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Passimpay API server error: " + e.getMessage() +
                    ", Status: " + e.getStatusCode() +
                    ", Response: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.error("Network error when calling Passimpay API", e);
            throw new RuntimeException("Network error connecting to Passimpay API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error initiating Passimpay withdrawal", e);
            throw new RuntimeException("Error initiating withdrawal: " + e.getMessage());
        }
    }

    /**
     * Handle a failed withdrawal by refunding the user
     */
    private Transaction handleFailedWithdrawal(Transaction transaction, String errorReason) {
        User user = transaction.getUser();
        String currency = transaction.getCurrency();
        BigDecimal amount = transaction.getAmount();
        BigDecimal fee = transaction.getFee();
        BigDecimal totalAmount = amount.add(fee);

        // Update transaction status
        transaction.setStatus("FAILED");
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setPaymentDetails(transaction.getPaymentDetails() + " | Error: " + errorReason);

        // Refund user's wallet balance
        walletBalanceService.creditWallet(user, currency, totalAmount);
        log.info("Refunded {} {} to user {} wallet", totalAmount, currency, user.getId());

        // Update GL entries
        glService.creditUserAccount(user, totalAmount);
        glService.debitFeeAccount(fee);

        // Notify the user only if we haven't already
        if (!notifiedTransactions.contains(transaction.getId())) {
            try {
                String title = "Withdrawal Failed - Transaction #" + transaction.getId();
                String message = String.format(
                        "Dear %s,\n\n" +
                                "We were unable to process your crypto withdrawal:\n\n" +
                                "Amount: %s %s\n" +
                                "Fee: %s %s\n" +
                                "Transaction ID: %s\n" +
                                "Reason: %s\n\n" +
                                "The funds have been returned to your %s wallet.\n\n" +
                                "Best regards,\n" +
                                "Papaymoni Team",
                        user.getFirstName(),
                        amount.setScale(2, RoundingMode.HALF_UP),
                        currency,
                        fee.setScale(2, RoundingMode.HALF_UP),
                        currency,
                        transaction.getId(),
                        errorReason,
                        currency
                );

                notificationService.createNotification(user, "EMAIL", title, message);
                notifiedTransactions.add(transaction.getId());
            } catch (Exception e) {
                log.error("Failed to send withdrawal failure notification: {}", e.getMessage());
            }
        }

        return transaction;
    }

    /**
     * Send notification for successful withdrawal
     */
    private void sendWithdrawalNotification(User user, BigDecimal amount, String currency,
                                            Long transactionId, BigDecimal fee) {
        try {
            notificationService.sendWithdrawalNotification(user, amount, currency, transactionId, fee);
            log.info("Sent withdrawal notification to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send withdrawal notification: {}", e.getMessage());
        }
    }

    /**
     * Generate a unique order ID for withdrawal tracking
     */
    private String generateOrderId(Long userId) {
        return "CRYPTO-WD-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

