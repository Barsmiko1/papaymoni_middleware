package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.PalmpayPayoutGatewayService;
import com.papaymoni.middleware.service.NgnPalmpayWithdrawalService;
import com.papaymoni.middleware.util.EaseIdSignUtil;
import com.papaymoni.middleware.util.PalmpayVerifySignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PalmpayPayoutGatewayServiceImpl implements PalmpayPayoutGatewayService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final PalmpayVerifySignUtil palmpayVerifySignUtil;
    private final ApplicationEventPublisher eventPublisher;

    // Use @Lazy to break the circular dependency
    @Lazy
    private NgnPalmpayWithdrawalService ngnPalmpayWithdrawalService;

    @Value("${palmpay.gateway.api.url}")
    private String baseUrl;

    @Value("${palmpay.gateway.api.countryCode}")
    private String countryCode;

    @Value("${palmpay.gateway.app.id}")
    private String apiToken;

    @Value("${palmpay.gateway.app.id}")
    private String appId;

    @Value("${palmpay.gateway.private.key}")
    private String privateKeyBase64;

    @Value("${palmpay.gateway.public.key}")
    private String palmpayPublicKey;

    @Value("${palmpay.gateway.payout.api.notifyUrl}")
    private String notifyUrl;

    // Constructor with required dependencies
    public PalmpayPayoutGatewayServiceImpl(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            TransactionRepository transactionRepository,
            PalmpayVerifySignUtil palmpayVerifySignUtil,
            ApplicationEventPublisher eventPublisher) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.transactionRepository = transactionRepository;
        this.palmpayVerifySignUtil = palmpayVerifySignUtil;
        this.eventPublisher = eventPublisher;
    }

    // Setter injection for WithdrawalService to break circular dependency
    @Autowired
    public void setWithdrawalService(@Lazy NgnPalmpayWithdrawalService ngnPalmpayWithdrawalService) {
        this.ngnPalmpayWithdrawalService = ngnPalmpayWithdrawalService;
    }

    @Override
    public List<BankDto> queryBankList() {
        log.info("Querying bank list from PalmPay");

        try {
            String url = baseUrl + "/api/v2/general/merchant/queryBankList";

            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("requestTime", Instant.now().toEpochMilli());
            requestParams.put("version", "V1.1");
            requestParams.put("nonceStr", generateNonceStr());
            requestParams.put("businessType", 0);

            HttpHeaders headers = createPalmpayHeaders(requestParams);

            // Convert request params to JSON
            String requestBody;
            try {
                requestBody = objectMapper.writeValueAsString(requestParams);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize request params to JSON", e);
                throw new IOException("Failed to serialize request parameters", e);
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.info("Bank list response: {}", response.getBody());

            // Parse response and extract bank list
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            if (!responseJson.get("respCode").asText().equals("00000000")) {
                log.error("Failed to retrieve bank list: {}", responseJson.get("respMsg").asText());
                return Collections.emptyList();
            }

            List<BankDto> banks = new ArrayList<>();

            if (responseJson.has("data") && responseJson.get("data").isArray()) {
                responseJson.get("data").forEach(bankNode -> {
                    BankDto bank = new BankDto();
                    bank.setBankCode(bankNode.get("bankCode").asText());
                    bank.setBankName(bankNode.get("bankName").asText());

                    if (bankNode.has("bankUrl")) {
                        bank.setBankUrl(bankNode.get("bankUrl").asText());
                    }
                    if (bankNode.has("bgUrl")) {
                        bank.setBgUrl(bankNode.get("bgUrl").asText());
                    }

                    banks.add(bank);
                });
            } else {
                // Handle case where a single bank object is returned instead of an array
                JsonNode dataNode = responseJson.get("data");
                if (dataNode != null && dataNode.isObject()) {
                    BankDto bank = new BankDto();
                    bank.setBankCode(dataNode.get("bankCode").asText());
                    bank.setBankName(dataNode.get("bankName").asText());

                    if (dataNode.has("bankUrl")) {
                        bank.setBankUrl(dataNode.get("bankUrl").asText());
                    }
                    if (dataNode.has("bgUrl")) {
                        bank.setBgUrl(dataNode.get("bgUrl").asText());
                    }

                    banks.add(bank);
                }
            }

            return banks;
        } catch (Exception e) {
            log.error("Error querying bank list: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public BankAccountQueryDto queryBankAccount(String bankCode, String accountNumber) {
        log.info("Querying bank account: {}, account: {}", bankCode, accountNumber);

        try {
            String url = baseUrl + "/api/v2/payment/merchant/payout/queryBankAccount";

            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("requestTime", Instant.now().toEpochMilli());
            requestParams.put("version", "V1.1");
            requestParams.put("nonceStr", generateNonceStr());
            requestParams.put("bankCode", bankCode);
            requestParams.put("bankAccNo", accountNumber);

            HttpHeaders headers = createPalmpayHeaders(requestParams);

            // Convert request params to JSON
            String requestBody;
            try {
                requestBody = objectMapper.writeValueAsString(requestParams);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize request params to JSON", e);
                throw new IOException("Failed to serialize request parameters", e);
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.info("Bank account query response: {}", response.getBody());

            // Parse response and extract account details
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            BankAccountQueryDto accountDetails = new BankAccountQueryDto();
            accountDetails.setBankCode(bankCode);
            accountDetails.setBankAccNo(accountNumber);

            if (responseJson.get("respCode").asText().equals("00000000")) {
                JsonNode data = responseJson.get("data");
                accountDetails.setStatus(data.get("Status").asText());
                accountDetails.setAccountName(data.get("accountName").asText());
                return accountDetails;
            } else {
                log.error("Failed to query bank account: {}", responseJson.get("respMsg").asText());
                accountDetails.setStatus("Failed");
                return accountDetails;
            }
        } catch (Exception e) {
            log.error("Error querying bank account: {}", e.getMessage(), e);
            BankAccountQueryDto accountDetails = new BankAccountQueryDto();
            accountDetails.setBankCode(bankCode);
            accountDetails.setBankAccNo(accountNumber);
            accountDetails.setStatus("Error");
            return accountDetails;
        }
    }

    @Override
    public PalmpayPayoutResponseDto initiatePayoutTransaction(String orderId, String accountName, String bankCode,
                                                              String accountNumber, String phoneNumber,
                                                              BigDecimal amount, String currency, String remark) {
        log.info("Initiating payout transaction for orderId: {}, amount: {} {}", orderId, amount, currency);

        try {
            String url = baseUrl + "/api/v2/merchant/payment/payout";

            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("requestTime", Instant.now().toEpochMilli());
            requestParams.put("version", "V1.1");
            requestParams.put("nonceStr", generateNonceStr());
            requestParams.put("orderId", orderId);
            requestParams.put("payeeName", accountName);
            requestParams.put("payeeBankCode", bankCode);
            requestParams.put("payeeBankAccNo", accountNumber);
            requestParams.put("payeePhoneNo", phoneNumber != null ? phoneNumber : "");
            requestParams.put("amount", amount.multiply(new BigDecimal(100)).intValue()); // Convert to minor units (kobo)
            requestParams.put("currency", currency);
            requestParams.put("notifyUrl", notifyUrl);
            requestParams.put("remark", remark != null ? remark : "");

            HttpHeaders headers = createPalmpayHeaders(requestParams);

            // Convert request params to JSON
            String requestBody;
            try {
                requestBody = objectMapper.writeValueAsString(requestParams);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize request params to JSON", e);
                throw new IOException("Failed to serialize request parameters", e);
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.info("Payout transaction response: {}", response.getBody());

            // Parse response
            return objectMapper.readValue(response.getBody(), PalmpayPayoutResponseDto.class);

        } catch (Exception e) {
            log.error("Error initiating payout transaction: {}", e.getMessage(), e);
            PalmpayPayoutResponseDto errorResponse = new PalmpayPayoutResponseDto();
            errorResponse.setRespCode("99999999");
            errorResponse.setRespMsg("Internal server error: " + e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public PalmpayPayoutResponseDto queryTransactionStatus(String orderId, String orderNo) {
        log.info("Querying transaction status for orderId: {}, orderNo: {}", orderId, orderNo);

        try {
            String url = baseUrl + "/api/v2/merchant/payment/queryPayStatus";

            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("requestTime", Instant.now().toEpochMilli());
            requestParams.put("version", "V1.1");
            requestParams.put("nonceStr", generateNonceStr());
            requestParams.put("orderId", orderId);
            requestParams.put("orderNo", orderNo);

            HttpHeaders headers = createPalmpayHeaders(requestParams);

            // Convert request params to JSON
            String requestBody;
            try {
                requestBody = objectMapper.writeValueAsString(requestParams);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize request params to JSON", e);
                throw new IOException("Failed to serialize request parameters", e);
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.info("Transaction status query response: {}", response.getBody());

            // Parse response
            return objectMapper.readValue(response.getBody(), PalmpayPayoutResponseDto.class);

        } catch (Exception e) {
            log.error("Error querying transaction status: {}", e.getMessage(), e);
            PalmpayPayoutResponseDto errorResponse = new PalmpayPayoutResponseDto();
            errorResponse.setRespCode("99999999");
            errorResponse.setRespMsg("Internal server error: " + e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public boolean processPayoutWebhook(PalmpayPayoutWebhookDto webhookDto) {
        log.info("Processing payout webhook: {}", webhookDto);

        try {
            // Verify webhook signature
            if (!verifyWebhookSignature(webhookDto)) {
                log.error("Invalid webhook signature");
                return false;
            }

            // Find corresponding transaction
            Optional<Transaction> transactionOpt = transactionRepository.findByExternalReference(webhookDto.getOrderId());
            if (!transactionOpt.isPresent()) {
                log.error("Transaction not found for orderId: {}", webhookDto.getOrderId());
                return false;
            }

            Transaction transaction = transactionOpt.get();

            // Check if transaction is already processed
            if ("COMPLETED".equals(transaction.getStatus()) || "FAILED".equals(transaction.getStatus())) {
                log.info("Transaction {} already in final state: {}", transaction.getId(), transaction.getStatus());
                return true; // Already processed, return success to avoid retries
            }

            // Process the webhook based on orderStatus
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("orderStatus", webhookDto.getOrderStatus());
            responseData.put("orderNo", webhookDto.getOrderNo());
            responseData.put("orderId", webhookDto.getOrderId());
            responseData.put("sessionId", webhookDto.getSessionId());
            responseData.put("completeTime", webhookDto.getCompleteTime());

            if (webhookDto.getOrderStatus() == 2) {
                // Success case - update transaction asynchronously
                scheduleTransactionUpdate(transaction.getId(), responseData, true);
                return true;
            } else if (webhookDto.getOrderStatus() == 3) {
                // Failure case - schedule status check with a delay
                scheduleTransactionUpdate(transaction.getId(), responseData, false);
                return true;
            } else {
                // Unknown status
                log.warn("Received unknown order status: {} for transaction: {}",
                        webhookDto.getOrderStatus(), transaction.getId());
                return true; // Return success to prevent retries
            }
        } catch (Exception e) {
            log.error("Error processing payout webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Schedule a transaction update to avoid circular dependency issues
     */
    @Async
    private void scheduleTransactionUpdate(Long transactionId, Map<String, Object> responseData, boolean isSuccess) {
        try {
            // Add a small delay to ensure transaction consistency
            Thread.sleep(1000);

            if (isSuccess) {
                // Complete the withdrawal
                Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
                if (transaction != null) {
                    ngnPalmpayWithdrawalService.completeWithdrawal(transaction, responseData);
                }
            } else {
                // Check and update transaction status
                ngnPalmpayWithdrawalService.checkAndUpdateTransactionStatus(transactionId);
            }
        } catch (Exception e) {
            log.error("Error in scheduled transaction update for transaction {}: {}", transactionId, e.getMessage());
        }
    }

    /**
     * Verify webhook signature using PalmpayVerifySignUtil
     */
    private boolean verifyWebhookSignature(PalmpayPayoutWebhookDto webhookDto) {
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
     * Create headers for PalmPay API requests
     */
    private HttpHeaders createPalmpayHeaders(Map<String, Object> requestParams) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("countryCode", countryCode);
        headers.set("Authorization", "Bearer " + apiToken);

        // Generate signature
        try {
            String signature = EaseIdSignUtil.generateSign(requestParams, privateKeyBase64,
                    EaseIdSignUtil.SignType.RSA);
            headers.set("Signature", signature);
        } catch (Exception e) {
            log.error("Failed to generate signature for Palmpay API request", e);
            // Use a fallback signature in case of error
            headers.set("Signature", "SIGNATURE_ERROR");
        }

        return headers;
    }

    /**
     * Generate a random nonce string for API requests
     */
    private String generateNonceStr() {
        String allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();

        return random.ints(32, 0, allowedChars.length())
                .mapToObj(i -> String.valueOf(allowedChars.charAt(i)))
                .collect(Collectors.joining());
    }
}
