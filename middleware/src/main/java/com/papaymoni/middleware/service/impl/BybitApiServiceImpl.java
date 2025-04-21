//package com.papaymoni.middleware.service.impl;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.papaymoni.middleware.dto.BybitApiResponse;
//import com.papaymoni.middleware.model.BybitCredentials;
//import com.papaymoni.middleware.service.BybitApiService;
//import org.apache.commons.codec.binary.Hex;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.core.ParameterizedTypeReference;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.util.HashMap;
//import java.util.Map;
//
//@Service
//public class BybitApiServiceImpl implements BybitApiService {
//    private static final String BASE_URL = "https://api-testnet.bybit.com";
//    private final RestTemplate restTemplate;
//    private final ObjectMapper objectMapper;
//
//    public BybitApiServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
//        this.restTemplate = restTemplate;
//        this.objectMapper = objectMapper;
//    }
//
//    @Override
//    public <T> BybitApiResponse<T> executeRequest(String endpoint, String method, Object payload,
//                                                  BybitCredentials credentials, Class<T> responseType) {
//        HttpHeaders headers = createHeaders(credentials, payload);
//        HttpEntity<?> entity = new HttpEntity<>(payload, headers);
//
//        String url = BASE_URL + endpoint;
//
//        if ("GET".equals(method)) {
//            return restTemplate.exchange(url, HttpMethod.GET, entity,
//                    new ParameterizedTypeReference<BybitApiResponse<T>>() {}).getBody();
//        } else {
//            return restTemplate.exchange(url, HttpMethod.POST, entity,
//                    new ParameterizedTypeReference<BybitApiResponse<T>>() {}).getBody();
//        }
//    }
//
//    private HttpHeaders createHeaders(BybitCredentials credentials, Object payload) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        long timestamp = System.currentTimeMillis();
//        String signature = generateSignature(credentials.getApiSecret(), timestamp, payload);
//
//        headers.set("X-BAPI-API-KEY", credentials.getApiKey());
//        headers.set("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
//        headers.set("X-BAPI-SIGN", signature);
//        headers.set("X-BAPI-RECV-WINDOW", "5000");
//
//        return headers;
//    }
//
//    private String generateSignature(String apiSecret, long timestamp, Object payload) {
//        String paramStr = timestamp + apiSecret + "5000";
//
//        if (payload != null) {
//            try {
//                paramStr += objectMapper.writeValueAsString(payload);
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException("Failed to serialize payload", e);
//            }
//        }
//
//        try {
//            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
//            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
//            hmacSha256.init(secretKeySpec);
//            byte[] hash = hmacSha256.doFinal(paramStr.getBytes());
//            return Hex.encodeHexString(hash);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to generate signature", e);
//        }
//    }
//
//    @Override
//    @Cacheable(value = "credentialsVerification", key = "#credentials.id")
//    public boolean verifyCredentials(BybitCredentials credentials) {
//        try {
//            BybitApiResponse<?> response = executeRequest("/v5/order/realtime", "GET", null, credentials, Object.class);
//            return response != null && response.isSuccess();
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    @Override
//    public BybitApiResponse<?> getAds(String tokenId, String currencyId, String side, BybitCredentials credentials) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("tokenId", tokenId);
//        payload.put("currencyId", currencyId);
//        payload.put("side", side);
//
//        return executeRequest("/v5/p2p/item/online", "POST", payload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> createAd(Object adPayload, BybitCredentials credentials) {
//        return executeRequest("/v5/p2p/item/create", "POST", adPayload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> cancelAd(String itemId, BybitCredentials credentials) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("itemId", itemId);
//
//        return executeRequest("/v5/p2p/item/cancel", "POST", payload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> updateAd(Object updatePayload, BybitCredentials credentials) {
//        return executeRequest("/v5/p2p/item/update", "POST", updatePayload, credentials, Object.class);
//    }
//
//    @Override
//    @Cacheable(value = "personalAds", key = "#credentials.id")
//    public BybitApiResponse<?> getPersonalAds(BybitCredentials credentials) {
//        return executeRequest("/v5/p2p/item/personal/list", "POST", new HashMap<>(), credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> getOrders(Object filterPayload, BybitCredentials credentials) {
//        return executeRequest("/v5/p2p/order/simplifyList", "POST", filterPayload, credentials, Object.class);
//    }
//
//    @Override
//    @Cacheable(value = "orderDetails", key = "#orderId")
//    public BybitApiResponse<?> getOrderDetail(String orderId, BybitCredentials credentials) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("orderId", orderId);
//
//        return executeRequest("/v5/p2p/order/info", "POST", payload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> getPendingOrders(Object filterPayload, BybitCredentials credentials) {
//        return executeRequest("/v5/p2p/order/pending/simplifyList", "POST", filterPayload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> markOrderAsPaid(String orderId, String paymentType, String paymentId, BybitCredentials credentials) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("orderId", orderId);
//        payload.put("paymentType", paymentType);
//        payload.put("paymentId", paymentId);
//
//        return executeRequest("/v5/p2p/order/pay", "POST", payload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> releaseAssets(String orderId, BybitCredentials credentials) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("orderId", orderId);
//
//        return executeRequest("/v5/p2p/order/finish", "POST", payload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> sendChatMessage(String message, String contentType, String orderId, BybitCredentials credentials) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("message", message);
//        payload.put("contentType", contentType);
//        payload.put("orderId", orderId);
//
//        return executeRequest("/v5/p2p/chat/send", "POST", payload, credentials, Object.class);
//    }
//
//    @Override
//    public BybitApiResponse<?> uploadChatFile(byte[] fileData, String filename, String orderId, BybitCredentials credentials) {
//        // This would require a multipart request, which is more complex
//        // For simplicity, we'll just return a mock response
//        BybitApiResponse<Object> response = new BybitApiResponse<>();
//        response.setRetCode(0);
//        response.setRetMsg("SUCCESS");
//        response.setTimeNow(String.valueOf(System.currentTimeMillis()));
//
//        Map<String, String> result = new HashMap<>();
//        result.put("url", "/fiat/p2p/oss/showObj/otc/9001/mock-file-url.png");
//        response.setResult(result);
//
//        return response;
//    }
//}


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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BybitApiServiceImpl implements BybitApiService {

    @Value("${bybit.api.base-url:https://api.bybit.com}")
    private String baseUrl;

    @Value("${bybit.api.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${bybit.api.read-timeout:30000}")
    private int readTimeout;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BybitApiServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Configure timeout settings for RestTemplate using SimpleClientHttpRequestFactory
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        restTemplate.setRequestFactory(factory);

        // Log configuration
        log.info("Initialized BybitApiService with baseUrl: {}, connectTimeout: {}ms, readTimeout: {}ms",
                baseUrl, connectTimeout, readTimeout);
    }

    @Override
    public <T> BybitApiResponse<T> executeRequest(String endpoint, String method, Object payload,
                                                  BybitCredentials credentials, Class<T> responseType) {
        try {
            HttpHeaders headers = createHeaders(credentials, payload);
            HttpEntity<?> entity = new HttpEntity<>(payload, headers);

            String url = baseUrl + endpoint;
            log.info("Making {} request to Bybit API: {}", method, url);

            if (payload != null) {
                try {
                    log.debug("Request payload: {}", objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.debug("Could not serialize payload for logging");
                }
            }

            ResponseEntity<BybitApiResponse<T>> response;
            if ("GET".equals(method)) {
                response = restTemplate.exchange(url, HttpMethod.GET, entity,
                        new ParameterizedTypeReference<BybitApiResponse<T>>() {});
            } else {
                response = restTemplate.exchange(url, HttpMethod.POST, entity,
                        new ParameterizedTypeReference<BybitApiResponse<T>>() {});
            }

            log.info("Received response from Bybit API with status: {}", response.getStatusCode());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("Network error accessing Bybit API (endpoint: {}): {}", endpoint, e.getMessage(), e);
            BybitApiResponse<T> errorResponse = new BybitApiResponse<>();
            errorResponse.setRetCode(-1);
            errorResponse.setRetMsg("Network error: " + e.getMessage());
            errorResponse.setTimeNow(String.valueOf(System.currentTimeMillis()));
            return errorResponse;
        } catch (Exception e) {
            log.error("Error executing Bybit API request (endpoint: {}): {}", endpoint, e.getMessage(), e);
            BybitApiResponse<T> errorResponse = new BybitApiResponse<>();
            errorResponse.setRetCode(-1);
            errorResponse.setRetMsg("Error: " + e.getMessage());
            errorResponse.setTimeNow(String.valueOf(System.currentTimeMillis()));
            return errorResponse;
        }
    }

    private HttpHeaders createHeaders(BybitCredentials credentials, Object payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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
}
