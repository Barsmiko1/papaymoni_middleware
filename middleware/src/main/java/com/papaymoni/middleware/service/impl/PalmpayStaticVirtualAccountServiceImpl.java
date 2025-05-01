package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.VirtualAccountResponse;
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

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class PalmpayStaticVirtualAccountServiceImpl implements PalmpayStaticVirtualAccountService {

    @Value("${palmpay.gateway.base.url:https://open-gw-daily.palmpay-inc.com}")
    private String palmpayGatewayBaseUrl;

    private static final String CREATE_ENDPOINT = "/api/v2/virtual/account/label/create";
    private static final String UPDATE_ENDPOINT = "/api/v2/virtual/account/label/update";
    private static final String DELETE_ENDPOINT = "/api/v2/virtual/account/label/delete";
    private static final String QUERY_ENDPOINT = "/api/v2/virtual/account/label/queryOne";

    @Value("${palmpay.gateway.app.id:L240927093144197211431}")
    private String appId;

    @Value("${palmpay.gateway.private.key}")
    private String privateKeyBase64;

    @Value("${palmpay.gateway.success.code:00000000}")
    private String successCode;

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    // Circuit breaker fields
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private volatile long circuitOpenTimestamp = 0;
    private static final int FAILURE_THRESHOLD = 5;
    private static final long RESET_TIMEOUT_MS = 60000; // 1 minute

    public PalmpayStaticVirtualAccountServiceImpl(
            ObjectMapper objectMapper,
            EncryptionService encryptionService) {
        this.objectMapper = objectMapper;
        this.encryptionService = encryptionService;

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
    }

    @Override
    @Cacheable(value = "virtualAccountCreations", key = "#user.id + '-' + #currency")
    public VirtualAccountResponse createVirtualAccount(User user, String currency) throws IOException {
        // Check circuit breaker
        if (isCircuitOpen()) {
            log.warn("Circuit breaker open, rejecting request to create virtual account");
            throw new IOException("Service temporarily unavailable due to downstream issues");
        }

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
        requestParams.put("virtualAccountName", "Palmpay");
        requestParams.put("version", "V2.0");
        requestParams.put("customerName", customerName);
        requestParams.put("email", email);
        requestParams.put("nonceStr", generateNonce());

        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(CREATE_ENDPOINT, requestParams);

            if (isSuccessResponse(responseMap)) {
                Map<String, Object> resultData = (Map<String, Object>) responseMap.get("data");

                VirtualAccountResponse response = new VirtualAccountResponse();
                response.setAccountNumber((String) resultData.get("virtualAccountNo"));
                response.setBankName("Palmpay");
                response.setBankCode("100004"); // Default bank code for Palmpay
                response.setAccountName((String) resultData.get("virtualAccountName"));

                log.info("Virtual account created successfully: {}", response.getAccountNumber());
                recordSuccess();
                return response;
            } else {
                String errorMsg = responseMap.containsKey("respMsg")
                        ? (String) responseMap.get("respMsg")
                        : "Unknown error";
                log.error("Failed to create virtual account: {}", errorMsg);
                recordFailure();
                throw new IOException("Failed to create virtual account: " + errorMsg);
            }
        } catch (IOException e) {
            recordFailure();
            throw e;
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

        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(UPDATE_ENDPOINT, requestParams);

            boolean success = isSuccessResponse(responseMap);
            if (success) {
                log.info("Virtual account status updated successfully: {}", virtualAccountNo);
                recordSuccess();
            } else {
                String errorMsg = responseMap.containsKey("respMsg")
                        ? (String) responseMap.get("respMsg")
                        : "Unknown error";
                log.error("Failed to update virtual account status: {}", errorMsg);
                recordFailure();
            }

            return success;
        } catch (IOException e) {
            recordFailure();
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

        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(DELETE_ENDPOINT, requestParams);

            boolean success = isSuccessResponse(responseMap);
            if (success) {
                log.info("Virtual account deleted successfully: {}", virtualAccountNo);
                recordSuccess();
            } else {
                String errorMsg = responseMap.containsKey("respMsg")
                        ? (String) responseMap.get("respMsg")
                        : "Unknown error";
                log.error("Failed to delete virtual account: {}", errorMsg);
                recordFailure();
            }

            return success;
        } catch (IOException e) {
            recordFailure();
            throw e;
        }
    }

    @Override
    @Cacheable(value = "virtualAccountDetails", key = "#virtualAccountNo")
    public VirtualAccountResponse queryVirtualAccount(String virtualAccountNo) throws IOException {
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

        try {
            // Make API call
            Map<String, Object> responseMap = callPalmpayApi(QUERY_ENDPOINT, requestParams);

            if (isSuccessResponse(responseMap)) {
                Map<String, Object> resultData = (Map<String, Object>) responseMap.get("data");

                VirtualAccountResponse response = new VirtualAccountResponse();
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
                recordFailure();
                throw new IOException("Failed to query virtual account: " + errorMsg);
            }
        } catch (IOException e) {
            recordFailure();
            throw e;
        }
    }

    @Override
    public boolean isServiceAvailable() {
        return !isCircuitOpen();
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

        // Convert request params to JSON
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(requestParams);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request params to JSON", e);
            throw new IOException("Failed to serialize request parameters", e);
        }

        // Create HTTP request
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

            log.debug("Palmpay Gateway API response status: {}, body: {}", statusCode, responseBody);

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

    // Circuit breaker methods
    private boolean isCircuitOpen() {
        if (circuitOpen.get()) {
            // Check if enough time has passed to try again
            if (System.currentTimeMillis() - circuitOpenTimestamp > RESET_TIMEOUT_MS) {
                log.info("Circuit breaker reset timeout reached, attempting recovery");
                circuitOpen.set(false);
                failureCount.set(0);
                return false;
            }
            return true;
        }
        return false;
    }

    private void recordSuccess() {
        failureCount.set(0);
        circuitOpen.set(false);
    }

    private void recordFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD && !circuitOpen.get()) {
            log.warn("Circuit breaker threshold reached ({} failures), opening circuit", failures);
            circuitOpen.set(true);
            circuitOpenTimestamp = System.currentTimeMillis();
        }
    }
}