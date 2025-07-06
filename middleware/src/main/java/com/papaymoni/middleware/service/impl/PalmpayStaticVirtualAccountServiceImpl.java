package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.VirtualAccountResponseDto;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.EncryptionService;
import com.papaymoni.middleware.service.PalmpayStaticVirtualAccountService;
import com.papaymoni.middleware.util.EaseIdSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.VirtualAccountRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class PalmpayStaticVirtualAccountServiceImpl implements PalmpayStaticVirtualAccountService {

    @Value("${palmpay.gateway.api.url}")
    private String palmpayGatewayBaseUrl;

    private static final String CREATE_ENDPOINT = "/api/v2/virtual/account/label/create";
    private static final String UPDATE_ENDPOINT = "/api/v2/virtual/account/label/update";
    private static final String DELETE_ENDPOINT = "/api/v2/virtual/account/label/delete";
    private static final String QUERY_ENDPOINT = "/api/v2/virtual/account/label/queryOne";

    @Value("${palmpay.gateway.app.id}")
    private String appId;

    @Value("${palmpay.gateway.private.key}")
    private String privateKeyBase64;

    @Value("${palmpay.gateway.success.code:00000000}")
    private String successCode;

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    // Improved circuit breaker state management with lock
    private final ReentrantReadWriteLock circuitBreakerLock = new ReentrantReadWriteLock();
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private volatile long circuitOpenTimestamp = 0;
    private static final int FAILURE_THRESHOLD = 5;
    private static final long RESET_TIMEOUT_MS = 60000; // 1 minute
    private static final long HALF_OPEN_TIMEOUT_MS = 10000; // 10 seconds for half-open state

    // Track success/failure in half-open state
    private final AtomicBoolean halfOpenState = new AtomicBoolean(false);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private static final int REQUIRED_SUCCESS_THRESHOLD = 3;

    // Track service health for monitoring
    private final ConcurrentHashMap<String, Long> endpointLatency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> endpointErrors = new ConcurrentHashMap<>();

    private final VirtualAccountRepository virtualAccountRepository;
    private final ConcurrentHashMap<String, Lock> userCurrencyLocks = new ConcurrentHashMap<>();

    public PalmpayStaticVirtualAccountServiceImpl(
            ObjectMapper objectMapper,
            EncryptionService encryptionService,
            VirtualAccountRepository virtualAccountRepository) {
        this.objectMapper = objectMapper;
        this.encryptionService = encryptionService;
        this.virtualAccountRepository = virtualAccountRepository;

        // Configure HTTP client with optimized connection pooling
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(15000)
                .setConnectionRequestTimeout(5000)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(50);
        connectionManager.setValidateAfterInactivity(1000);

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("PalmpayStaticVirtualAccountService initialized with base URL: {}", palmpayGatewayBaseUrl);
        // Initialize error tracking for endpoints
        endpointErrors.put(CREATE_ENDPOINT, new AtomicInteger(0));
        endpointErrors.put(UPDATE_ENDPOINT, new AtomicInteger(0));
        endpointErrors.put(DELETE_ENDPOINT, new AtomicInteger(0));
        endpointErrors.put(QUERY_ENDPOINT, new AtomicInteger(0));
    }

    @Override
    @Transactional
    public VirtualAccountResponseDto createVirtualAccount(User user, String currency) throws IOException {
        // Check circuit breaker
        if (isCircuitOpen()) {
            log.warn("Circuit breaker open, rejecting request to create virtual account");
            throw new IOException("Service temporarily unavailable due to downstream issues");
        }

        // Create a unique key for this user and currency combination
        String lockKey = user.getId() + "-" + currency;

        // Get or create a lock for this specific user-currency combination
        Lock userCurrencyLock = userCurrencyLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());

        // Acquire the lock to ensure thread safety
        userCurrencyLock.lock();
        try {
            // First, check if the user already has a virtual account for this currency
            List<VirtualAccount> existingAccounts = virtualAccountRepository.findByUserIdAndCurrencyWithUser(user.getId(), currency);

            if (!existingAccounts.isEmpty()) {
                log.info("User {} already has a virtual account for currency {}, returning existing account details", user.getId(), currency);
                VirtualAccount existingAccount = existingAccounts.get(0);

                // Convert to response DTO
                VirtualAccountResponseDto response = new VirtualAccountResponseDto();
                response.setAccountNumber(existingAccount.getAccountNumber());
                response.setBankName(existingAccount.getBankName());
                response.setBankCode(existingAccount.getBankCode());
                response.setAccountName(existingAccount.getAccountName());
                response.setCurrency(existingAccount.getCurrency());
                response.setBalance(existingAccount.getBalance());
                response.setActive(existingAccount.isActive());
                response.setId(String.valueOf(existingAccount.getId()));
                response.setExistingAccount(true);

                return response;
            }

            // No existing account found, proceed with creation
            log.info("Creating new virtual account for user {} with currency {}", user.getId(), currency);

            // Get BVN - decrypt if necessary
            String bvn = getBvn(user);
            String customerName = user.getFirstName() + " " + user.getLastName();
            String email = user.getEmail();

            log.info("Creating virtual account for customer: {}, email: {}", customerName, email);

            // Generate request payload
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("requestTime", System.currentTimeMillis());
            requestParams.put("identityType", "personal");
            requestParams.put("licenseNumber", bvn);
            requestParams.put("virtualAccountName", customerName);
            requestParams.put("version", "V2.0");
            requestParams.put("customerName", customerName);
            requestParams.put("email", email);
            requestParams.put("nonceStr", generateNonce());

            long startTime = System.currentTimeMillis();
            try {
                // Make API call
                Map<String, Object> responseMap = callPalmpayApi(CREATE_ENDPOINT, requestParams);

                // Track latency
                endpointLatency.put(CREATE_ENDPOINT, System.currentTimeMillis() - startTime);

                if (isSuccessResponse(responseMap)) {
                    Map<String, Object> resultData = (Map<String, Object>) responseMap.get("data");

                    // Use your factory method
                    VirtualAccountResponseDto response = VirtualAccountResponseDto.fromPalmpayResponse(resultData);
                    // Ensure it's not flagged as existing
                    response.setExistingAccount(false);

                    log.info("Virtual account created successfully: {}", response.getAccountNumber());
                    recordSuccess();

                    return response;
                } else {
                    String errorMsg = responseMap.containsKey("respMsg")
                            ? (String) responseMap.get("respMsg")
                            : "Unknown error";
                    log.error("Failed to create virtual account: {}", errorMsg);
                    recordFailure(CREATE_ENDPOINT);
                    throw new IOException("Failed to create virtual account: " + errorMsg);
                }
            } catch (IOException e) {
                recordFailure(CREATE_ENDPOINT);
                throw e;
            }
        } finally {
            // Cleanup logic - only attempt to remove if we can still get the lock
            boolean lockRemoved = false;

            if (userCurrencyLock.tryLock()) {
                try {
                    // We got the lock again, which means no one else is using it
                    // Safe to remove if this was the last operation for this user-currency
                    userCurrencyLocks.remove(lockKey);
                    lockRemoved = true;
                } finally {
                    userCurrencyLock.unlock();
                }
            }

            // Always release the original lock regardless of cleanup outcome
            userCurrencyLock.unlock();

            // Log the cleanup result for debugging
            if (lockRemoved) {
                log.debug("Lock cleanup successful for user-currency: {}", lockKey);
            }
        }
    }
    @Override
    public boolean updateVirtualAccountStatus(String virtualAccountNo, String status) throws IOException {
        // Check circuit breaker
        if (isCircuitOpen()) {
            log.warn("Circuit breaker open, rejecting request to update virtual account");
            throw new IOException("Service temporarily unavailable due to downstream issues");
        }

        log.info("Updating virtual account status: {} to {}", virtualAccountNo, status);

        // Generate request payload
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("requestTime", System.currentTimeMillis());
        requestParams.put("version", "V2.0");
        requestParams.put("nonceStr", generateNonce());
        requestParams.put("virtualAccountNo", virtualAccountNo);
        requestParams.put("status", status);

        long startTime = System.currentTimeMillis();
        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(UPDATE_ENDPOINT, requestParams);

            // Track latency
            endpointLatency.put(UPDATE_ENDPOINT, System.currentTimeMillis() - startTime);

            boolean success = isSuccessResponse(responseMap);
            if (success) {
                log.info("Virtual account status updated successfully: {}", virtualAccountNo);
                recordSuccess();
            } else {
                String errorMsg = responseMap.containsKey("respMsg")
                        ? (String) responseMap.get("respMsg")
                        : "Unknown error";
                log.error("Failed to update virtual account status: {}", errorMsg);
                recordFailure(UPDATE_ENDPOINT);
            }

            return success;
        } catch (IOException e) {
            recordFailure(UPDATE_ENDPOINT);
            throw e;
        }
    }

    @Override
    public boolean deleteVirtualAccount(String virtualAccountNo) throws IOException {
        // Check circuit breaker
        if (isCircuitOpen()) {
            log.warn("Circuit breaker open, rejecting request to delete virtual account");
            throw new IOException("Service temporarily unavailable due to downstream issues");
        }

        log.info("Deleting virtual account: {}", virtualAccountNo);

        // Generate request payload
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("requestTime", System.currentTimeMillis());
        requestParams.put("version", "V2.0");
        requestParams.put("nonceStr", generateNonce());
        requestParams.put("virtualAccountNo", virtualAccountNo);

        long startTime = System.currentTimeMillis();
        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(DELETE_ENDPOINT, requestParams);

            // Track latency
            endpointLatency.put(DELETE_ENDPOINT, System.currentTimeMillis() - startTime);

            boolean success = isSuccessResponse(responseMap);
            if (success) {
                log.info("Virtual account deleted successfully: {}", virtualAccountNo);
                recordSuccess();
            } else {
                String errorMsg = responseMap.containsKey("respMsg")
                        ? (String) responseMap.get("respMsg")
                        : "Unknown error";
                log.error("Failed to delete virtual account: {}", errorMsg);
                recordFailure(DELETE_ENDPOINT);
            }

            return success;
        } catch (IOException e) {
            recordFailure(DELETE_ENDPOINT);
            throw e;
        }
    }

    @Override
    @Cacheable(value = "virtualAccountDetails", key = "#virtualAccountNo")
    public VirtualAccountResponseDto queryVirtualAccount(String virtualAccountNo) throws IOException {
        // Check circuit breaker
        if (isCircuitOpen()) {
            log.warn("Circuit breaker open, rejecting request to query virtual account");
            throw new IOException("Service temporarily unavailable due to downstream issues");
        }

        log.info("Querying virtual account: {}", virtualAccountNo);

        // Generate request payload
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("requestTime", System.currentTimeMillis());
        requestParams.put("version", "V2.0");
        requestParams.put("nonceStr", generateNonce());
        requestParams.put("virtualAccountNo", virtualAccountNo);

        long startTime = System.currentTimeMillis();
        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(QUERY_ENDPOINT, requestParams);

            // Track latency
            endpointLatency.put(QUERY_ENDPOINT, System.currentTimeMillis() - startTime);

            if (isSuccessResponse(responseMap)) {
                Map<String, Object> resultData = (Map<String, Object>) responseMap.get("data");

                VirtualAccountResponseDto response = new VirtualAccountResponseDto();
                response.setAccountNumber((String) resultData.get("virtualAccountNo"));
                response.setBankName("Palmpay");
                response.setBankCode("100004"); // Default bank code for Palmpay
                response.setAccountName((String) resultData.get("virtualAccountName"));

                log.info("Virtual account query successful: {}", response.getAccountNumber());
                recordSuccess();
                return response;
            } else {
                String errorMsg = responseMap.containsKey("respMsg")
                        ? (String) responseMap.get("respMsg")
                        : "Unknown error";
                log.error("Failed to query virtual account: {}", errorMsg);
                recordFailure(QUERY_ENDPOINT);
                throw new IOException("Failed to query virtual account: " + errorMsg);
            }
        } catch (IOException e) {
            recordFailure(QUERY_ENDPOINT);
            throw e;
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return !isCircuitOpen();
    }

    /**
     * Get service health information for monitoring
     * @return Map containing circuit breaker state and endpoint metrics
     */
    public Map<String, Object> getServiceHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("circuitOpen", circuitOpen.get());
        healthInfo.put("failureCount", failureCount.get());
        healthInfo.put("halfOpenState", halfOpenState.get());
        healthInfo.put("endpointLatency", new HashMap<>(endpointLatency));

        // Copy error counts atomically
        Map<String, Integer> errorCounts = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : endpointErrors.entrySet()) {
            errorCounts.put(entry.getKey(), entry.getValue().get());
        }
        healthInfo.put("endpointErrors", errorCounts);

        return healthInfo;
    }

    /**
     * Call Palmpay API with the given endpoint and request parameters
     * @param endpoint API endpoint path
     * @param requestParams Request parameters
     * @return Response as a Map
     * @throws IOException If there's an error communicating with Palmpay Gateway
     */
    private Map<String, Object> callPalmpayApi(String endpoint, Map<String, Object> requestParams) throws IOException {
        // Generate signature
        String signature;
        try {
            signature = EaseIdSignUtil.generateSign(requestParams, privateKeyBase64,
                    EaseIdSignUtil.SignType.RSA);
        } catch (Exception e) {
            log.error("Failed to generate signature for Palmpay API request", e);
            throw new IOException("Failed to generate signature: " + e.getMessage());
        }

        // Convert request params to JSON with security handling
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(requestParams);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request params to JSON", e);
            throw new IOException("Failed to serialize request parameters", e);
        }

        // Create HTTP request with proper headers and timeout
        HttpPost httpPost = new HttpPost(palmpayGatewayBaseUrl + endpoint);

        // Set headers
        httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
        httpPost.setHeader("countryCode", "NG");
        httpPost.setHeader("Signature", signature);
        httpPost.setHeader("Authorization", "Bearer " + appId);

        // Set request body
        try {
            StringEntity entity = new StringEntity(requestBody, "UTF-8");
            httpPost.setEntity(entity);
        } catch (Exception e) {
            log.error("Encoding error when creating request entity", e);
            throw new IOException("Failed to create request entity", e);
        }

        // Execute request with error handling
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity responseEntity = response.getEntity();

            String responseBody;
            try {
                responseBody = EntityUtils.toString(responseEntity, "UTF-8");
            } catch (IOException e) {
                log.error("Failed to read response body", e);
                throw new IOException("Failed to read response from Palmpay Gateway", e);
            }

            log.debug("Palmpay Gateway API response status: {}", statusCode);

            if (statusCode >= 400) {
                log.error("Palmpay Gateway API error: {} - {}", statusCode, responseBody);
                throw new IOException("Palmpay Gateway API returned error: " + statusCode + " - " + responseBody);
            }

            // Parse response
            try {
                return objectMapper.readValue(responseBody, Map.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse response JSON", e);
                throw new IOException("Failed to parse response: " + e.getMessage());
            }
        } catch (IOException e) {
            log.error("HTTP client error during Palmpay API call", e);
            throw e;
        }
    }

    /**
     * Check if the response indicates success
     * @param responseMap Response from Palmpay API
     * @return true if the response indicates success
     */
    private boolean isSuccessResponse(Map<String, Object> responseMap) {
        return responseMap != null &&
                responseMap.containsKey("respCode") &&
                successCode.equals(responseMap.get("respCode"));
    }

    /**
     * Generate a random 32-byte nonce string
     */
    private String generateNonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);

        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }

    /**
     * Get BVN from user, decrypting if necessary
     */
    private String getBvn(User user) {
        if (user.getBvn() == null) {
            throw new IllegalArgumentException("User does not have a BVN");
        }

        try {
            if (user.getBvn().startsWith("ENC:")) {
                return encryptionService.decrypt(user.getBvn());
            } else {
                return user.getBvn();
            }
        } catch (Exception e) {
            log.error("Error decrypting BVN for user: {}", user.getId(), e);
            throw new IllegalStateException("Failed to access BVN information", e);
        }
    }

    // Improved circuit breaker methods with proper synchronization and state transitions
    private boolean isCircuitOpen() {
        circuitBreakerLock.readLock().lock();
        try {
            if (circuitOpen.get()) {
                long currentTime = System.currentTimeMillis();
                // Check if enough time has passed to enter half-open state
                if (currentTime - circuitOpenTimestamp > RESET_TIMEOUT_MS) {
                    circuitBreakerLock.readLock().unlock();
                    circuitBreakerLock.writeLock().lock();
                    try {
                        // Double-check to prevent race condition
                        if (circuitOpen.get() && currentTime - circuitOpenTimestamp > RESET_TIMEOUT_MS) {
                            log.info("Circuit breaker entering half-open state");
                            halfOpenState.set(true);
                            halfOpenSuccessCount.set(0);
                            // Keep circuit open but allow a test request
                            return false;
                        }
                    } finally {
                        circuitBreakerLock.writeLock().unlock();
                        circuitBreakerLock.readLock().lock();
                    }
                }
                return true;
            } else if (halfOpenState.get()) {
                // In half-open state, allow a limited number of requests
                return false;
            }
            return false;
        } finally {
            circuitBreakerLock.readLock().unlock();
        }
    }

    private void recordSuccess() {
        circuitBreakerLock.writeLock().lock();
        try {
            if (halfOpenState.get()) {
                // In half-open state, count successes until threshold is reached
                int successCount = halfOpenSuccessCount.incrementAndGet();
                if (successCount >= REQUIRED_SUCCESS_THRESHOLD) {
                    log.info("Circuit breaker closed after {} consecutive successes", successCount);
                    // Close the circuit
                    circuitOpen.set(false);
                    halfOpenState.set(false);
                    failureCount.set(0);
                }
            } else {
                // Normal success, reset failure count
                failureCount.set(0);
                circuitOpen.set(false);
            }
        } finally {
            circuitBreakerLock.writeLock().unlock();
        }
    }

    private void recordFailure(String endpoint) {
        circuitBreakerLock.writeLock().lock();
        try {
            // Track error for specific endpoint
            AtomicInteger endpointErrorCount = endpointErrors.get(endpoint);
            if (endpointErrorCount != null) {
                endpointErrorCount.incrementAndGet();
            }

            if (halfOpenState.get()) {
                // In half-open state, immediate failure reopens the circuit
                log.warn("Circuit breaker reopened due to failure in half-open state");
                circuitOpen.set(true);
                halfOpenState.set(false);
                circuitOpenTimestamp = System.currentTimeMillis();
                failureCount.set(FAILURE_THRESHOLD);
            } else {
                // Normal failure counter
                int failures = failureCount.incrementAndGet();
                if (failures >= FAILURE_THRESHOLD && !circuitOpen.get()) {
                    log.warn("Circuit breaker threshold reached ({} failures), opening circuit", failures);
                    circuitOpen.set(true);
                    circuitOpenTimestamp = System.currentTimeMillis();
                }
            }
        } finally {
            circuitBreakerLock.writeLock().unlock();
        }
    }
}
