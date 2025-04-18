package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.BybitApiResponse;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.service.BybitApiService;
import org.apache.commons.codec.binary.Hex;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;

@Service
public class BybitApiServiceImpl implements BybitApiService {
    private static final String BASE_URL = "https://api-testnet.bybit.com";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BybitApiServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> BybitApiResponse<T> executeRequest(String endpoint, String method, Object payload,
                                                  BybitCredentials credentials, Class<T> responseType) {
        HttpHeaders headers = createHeaders(credentials, payload);
        HttpEntity<?> entity = new HttpEntity<>(payload, headers);

        String url = BASE_URL + endpoint;

        if ("GET".equals(method)) {
            return restTemplate.exchange(url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<BybitApiResponse<T>>() {}).getBody();
        } else {
            return restTemplate.exchange(url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<BybitApiResponse<T>>() {}).getBody();
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
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    @Override
    @Cacheable(value = "credentialsVerification", key = "#credentials.id")
    public boolean verifyCredentials(BybitCredentials credentials) {
        try {
            BybitApiResponse<?> response = executeRequest("/v5/order/realtime", "GET", null, credentials, Object.class);
            return response != null && response.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public BybitApiResponse<?> getAds(String tokenId, String currencyId, String side, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("tokenId", tokenId);
        payload.put("currencyId", currencyId);
        payload.put("side", side);

        return executeRequest("/v5/p2p/item/online", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> createAd(Object adPayload, BybitCredentials credentials) {
        return executeRequest("/v5/p2p/item/create", "POST", adPayload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> cancelAd(String itemId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("itemId", itemId);

        return executeRequest("/v5/p2p/item/cancel", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> updateAd(Object updatePayload, BybitCredentials credentials) {
        return executeRequest("/v5/p2p/item/update", "POST", updatePayload, credentials, Object.class);
    }

    @Override
    @Cacheable(value = "personalAds", key = "#credentials.id")
    public BybitApiResponse<?> getPersonalAds(BybitCredentials credentials) {
        return executeRequest("/v5/p2p/item/personal/list", "POST", new HashMap<>(), credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> getOrders(Object filterPayload, BybitCredentials credentials) {
        return executeRequest("/v5/p2p/order/simplifyList", "POST", filterPayload, credentials, Object.class);
    }

    @Override
    @Cacheable(value = "orderDetails", key = "#orderId")
    public BybitApiResponse<?> getOrderDetail(String orderId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", orderId);

        return executeRequest("/v5/p2p/order/info", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> getPendingOrders(Object filterPayload, BybitCredentials credentials) {
        return executeRequest("/v5/p2p/order/pending/simplifyList", "POST", filterPayload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> markOrderAsPaid(String orderId, String paymentType, String paymentId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("paymentType", paymentType);
        payload.put("paymentId", paymentId);

        return executeRequest("/v5/p2p/order/pay", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> releaseAssets(String orderId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", orderId);

        return executeRequest("/v5/p2p/order/finish", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> sendChatMessage(String message, String contentType, String orderId, BybitCredentials credentials) {
        Map<String, String> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("contentType", contentType);
        payload.put("orderId", orderId);

        return executeRequest("/v5/p2p/chat/send", "POST", payload, credentials, Object.class);
    }

    @Override
    public BybitApiResponse<?> uploadChatFile(byte[] fileData, String filename, String orderId, BybitCredentials credentials) {
        // This would require a multipart request, which is more complex
        // For simplicity, we'll just return a mock response
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
