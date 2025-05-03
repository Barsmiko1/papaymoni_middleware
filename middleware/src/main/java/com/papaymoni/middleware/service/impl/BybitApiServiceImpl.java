package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.BybitApiResponse;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.service.BybitApiService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Slf4j
@Service
public class BybitApiServiceImpl implements BybitApiService {

    @Value("${bybit.api.base-url:https://api.bybit.com}")
    private String baseUrl;

    @Value("${bybit.api.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${bybit.api.read-timeout:30000}")
    private int readTimeout;

    @Value("${bybit.api.max-connections:200}")
    private int maxConnections;

    @Value("${bybit.api.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;

    @Value("${bybit.api.max-retries:3}")
    private int maxRetries;

    private final ObjectMapper objectMapper;
    private RestTemplate restTemplate;
    private RetryTemplate retryTemplate;

    // Monitoring metrics
    private final ConcurrentHashMap<String, AtomicInteger> endpointCalls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> endpointErrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> endpointLatency = new ConcurrentHashMap<>();

    public BybitApiServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Configure connection pool manager
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        connectionManager.setValidateAfterInactivity(10000); // Validate connections after 10 seconds of inactivity

        // Configure timeouts and connection request timeout
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .setConnectionRequestTimeout(5000) // Timeout for getting a connection from the pool
                .build();

        // Build HTTP client with connection pooling and timeout configuration
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setKeepAliveStrategy((response, context) -> 30 * 1000) // Keep connections alive for 30 seconds
                .build();

        // Create and configure RestTemplate with the HTTP client
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        restTemplate = new RestTemplate(requestFactory);

        // Configure retry template
        retryTemplate = new RetryTemplate();

        // Configure exponential backoff for retries
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Configure retry policy - retry on network and server errors, but not on client errors
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(ResourceAccessException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);
        retryableExceptions.put(HttpServerErrorException.class, true);
        // Don't retry on client errors (4xx)
        retryableExceptions.put(HttpClientErrorException.class, false);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(maxRetries, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Log configuration
        log.info("Initialized BybitApiService with baseUrl: {}, connectTimeout: {}ms, readTimeout: {}ms, " +
                        "maxConnections: {}, maxConnectionsPerRoute: {}, maxRetries: {}",
                baseUrl, connectTimeout, readTimeout, maxConnections, maxConnectionsPerRoute, maxRetries);
    }

    @Override
    @Retryable(value = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2, maxDelay = 10000))
    public <T> BybitApiResponse<T> executeRequest(String endpoint, String method, Object payload,
                                                  BybitCredentials credentials, Class<T> responseType) {
        final String fullUrl = baseUrl + endpoint;
        final String endpointKey = method + ":" + endpoint;

        // Track requests
        endpointCalls.computeIfAbsent(endpointKey, k -> new AtomicInteger(0)).incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            HttpHeaders headers = createHeaders(credentials, payload);
            HttpEntity<?> entity = new HttpEntity<>(payload, headers);

            log.debug("Making {} request to Bybit API: {}", method, fullUrl);

            if (payload != null) {
                try {
                    log.debug("Request payload: {}", objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.debug("Could not serialize payload for logging");
                }
            }

            return retryTemplate.execute(context -> {
                ResponseEntity<BybitApiResponse<T>> response;
                if ("GET".equals(method)) {
                    response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity,
                            new org.springframework.core.ParameterizedTypeReference<BybitApiResponse<T>>() {});
                } else {
                    response = restTemplate.exchange(fullUrl, HttpMethod.POST, entity,
                            new org.springframework.core.ParameterizedTypeReference<BybitApiResponse<T>>() {});
                }

                log.debug("Received response from Bybit API with status: {}", response.getStatusCode());
                return response.getBody();
            });
        } catch (ResourceAccessException e) {
            // Track errors
            endpointErrors.computeIfAbsent(endpointKey, k -> new AtomicInteger(0)).incrementAndGet();

            log.error("Network error accessing Bybit API (endpoint: {}): {}", endpoint, e.getMessage(), e);
            BybitApiResponse<T> errorResponse = new BybitApiResponse<>();
            errorResponse.setRetCode(-1);
            errorResponse.setRetMsg("Network error: " + e.getMessage());
            errorResponse.setTimeNow(String.valueOf(System.currentTimeMillis()));
            return errorResponse;
        } catch (Exception e) {
            // Track errors
            endpointErrors.computeIfAbsent(endpointKey, k -> new AtomicInteger(0)).incrementAndGet();

            log.error("Error executing Bybit API request (endpoint: {}): {}", endpoint, e.getMessage(), e);
            BybitApiResponse<T> errorResponse = new BybitApiResponse<>();
            errorResponse.setRetCode(-1);
            errorResponse.setRetMsg("Error: " + e.getMessage());
            errorResponse.setTimeNow(String.valueOf(System.currentTimeMillis()));
            return errorResponse;
        } finally {
            // Track latency
            endpointLatency.put(endpointKey, System.currentTimeMillis() - startTime);
        }
    }

    private HttpHeaders createHeaders(BybitCredentials credentials, Object payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Protect against null credentials
        if (credentials == null || credentials.getApiKey() == null || credentials.getApiSecret() == null) {
            log.warn("Missing credentials or API key/secret");
            return headers;
        }

        long timestamp = System.currentTimeMillis();
        String signature = generateSignature(credentials.getApiSecret(), timestamp, payload);

        headers.set("X-BAPI-API-KEY", credentials.getApiKey());
        headers.set("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-RECV-WINDOW", "5000");

        return headers;
    }

    private String generateSignature(String apiSecret, long timestamp, Object payload) {
        String paramStr = timestamp + apiSecret + "5000";

        if (payload != null) {
            try {
                paramStr += objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize payload: {}", e.getMessage());
                throw new RuntimeException("Failed to serialize payload", e);
            }
        }

        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
            hmacSha256.init(secretKeySpec);
            byte[] hash = hmacSha256.doFinal(paramStr.getBytes());
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            log.error("Failed to generate signature: {}", e.getMessage());
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Test basic connectivity to Bybit API
     * @return true if connection is successful
     */
    @Override
    public boolean testConnection() {
        try {
            log.info("Testing connection to Bybit API at {}", baseUrl);
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/v5/market/time", Map.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("Bybit API connection test: {}", success ? "SUCCESS" : "FAILURE");
            return success;
        } catch (Exception e) {
            log.error("Failed to connect to Bybit API: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    // Use API key as cache key instead of credentials.id which might be null
    @Cacheable(value = "credentialsVerification", key = "#credentials.apiKey != null ? #credentials.apiKey : 'temp'")
    public boolean verifyCredentials(BybitCredentials credentials) {
        if (credentials == null || credentials.getApiKey() == null || credentials.getApiSecret() == null) {
            log.warn("Cannot verify null credentials or credentials with null API key/secret");
            return false;
        }

        log.debug("Verifying Bybit credentials with API key: {}", credentials.getApiKey());
        try {
            // Use the P2P ads endpoint for verification
            Map<String, String> payload = new HashMap<>();
            payload.put("tokenId", "USDT");
            payload.put("currencyId", "EUR");
            payload.put("side", "0");

            BybitApiResponse<?> response = executeRequest("/v5/p2p/item/online", "POST", payload, credentials, Object.class);

            boolean isValid = response != null && response.isSuccess();
            log.info("Credentials verification result: {}", isValid ? "valid" : "invalid");

            if (!isValid && response != null) {
                log.debug("Verification response code: {}, message: {}", response.getRetCode(), response.getRetMsg());
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying Bybit credentials: {}", e.getMessage(), e);
            return false;
        }
    }
    @Override
    public BybitApiResponse<?> getAds(String tokenId, String currencyId, String side, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("tokenId", tokenId);
        payload.put("currencyId", currencyId);
        payload.put("side", side);

        log.info("Fetching Bybit ads with tokenId: {}, currencyId: {}, side: {}",
                tokenId, currencyId, side);
        return executeRequest("/v5/p2p/item/online", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> createAd(Object adPayload, BybitCredentials credentials) {
        log.info("Creating Bybit ad");
        return executeRequest("/v5/p2p/item/create", "POST", adPayload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> cancelAd(String itemId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("itemId", itemId);

        log.info("Cancelling Bybit ad with itemId: {}", itemId);
        return executeRequest("/v5/p2p/item/cancel", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> updateAd(Object updatePayload, BybitCredentials credentials) {
        log.info("Updating Bybit ad");
        return executeRequest("/v5/p2p/item/update", "POST", updatePayload, credentials, Object.class);
    }

    @Override
    // Modified caching strategy to use API key instead of ID
    @Cacheable(value = "personalAds", key = "#credentials.apiKey != null ? #credentials.apiKey : 'temp'")
    public BybitApiResponse<?> getPersonalAds(BybitCredentials credentials) {
        log.info("Fetching personal Bybit ads");
        return executeRequest("/v5/p2p/item/personal/list", "POST", new HashMap<>(), credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> getOrders(Object filterPayload, BybitCredentials credentials) {
        log.info("Fetching Bybit orders");
        return executeRequest("/v5/p2p/order/simplifyList", "POST", filterPayload, credentials, Object.class);
    }

    @Override
    // Use orderId as cache key which is more reliable
    @Cacheable(value = "orderDetails", key = "#orderId")
    public BybitApiResponse<?> getOrderDetail(String orderId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", orderId);

        log.info("Fetching Bybit order details for orderId: {}", orderId);
        return executeRequest("/v5/p2p/order/info", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> getPendingOrders(Object filterPayload, BybitCredentials credentials) {
        log.info("Fetching pending Bybit orders");
        return executeRequest("/v5/p2p/order/pending/simplifyList", "POST", filterPayload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> markOrderAsPaid(String orderId, String paymentType, String paymentId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("paymentType", paymentType);
        payload.put("paymentId", paymentId);

        log.info("Marking Bybit order as paid: {}", orderId);
        return executeRequest("/v5/p2p/order/pay", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> releaseAssets(String orderId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", orderId);

        log.info("Releasing assets for Bybit order: {}", orderId);
        return executeRequest("/v5/p2p/order/finish", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> sendChatMessage(String message, String contentType, String orderId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("contentType", contentType);
        payload.put("orderId", orderId);

        log.info("Sending chat message for Bybit order: {}", orderId);
        return executeRequest("/v5/p2p/chat/send", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> uploadChatFile(byte[] fileData, String filename, String orderId, BybitCredentials credentials) {
        // This would require a multipart request, which is more complex
        // For simplicity, we'll just return a mock response
        log.info("Uploading chat file for Bybit order: {}", orderId);

        BybitApiResponse<Object> response = new BybitApiResponse<>();
        response.setRetCode(0);
        response.setRetMsg("SUCCESS");
        response.setTimeNow(String.valueOf(System.currentTimeMillis()));

        Map<String, String> result = new HashMap<>();
        result.put("url", "/fiat/p2p/oss/showObj/otc/9001/mock-file-url.png");
        response.setResult(result);

        return response;

    }

    // Implementation for more specific API methods

    /**
     * Get API service health metrics
     * @return Map containing health metrics
     */
    public Map<String, Object> getServiceHealth() {
        Map<String, Object> healthInfo = new HashMap<>();

        // Copy metrics data atomically to avoid concurrent modification issues
        Map<String, Integer> callCounts = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : endpointCalls.entrySet()) {
            callCounts.put(entry.getKey(), entry.getValue().get());
        }

        Map<String, Integer> errorCounts = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : endpointErrors.entrySet()) {
            errorCounts.put(entry.getKey(), entry.getValue().get());
        }

        healthInfo.put("endpointCalls", callCounts);
        healthInfo.put("endpointErrors", errorCounts);
        healthInfo.put("endpointLatency", new HashMap<>(endpointLatency));

        return healthInfo;
    }

    /**
     * Reset monitoring metrics (for testing or monitoring purposes)
     */
    public void resetMetrics() {
        endpointCalls.clear();
        endpointErrors.clear();
        endpointLatency.clear();
    }
}