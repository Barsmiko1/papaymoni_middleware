package com.papaymoni.middleware.util;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PalmpayVerifySignUtil {

    private final ObjectMapper objectMapper;

    // Cache signature validations for performance and protection against replay attacks
    private final ConcurrentHashMap<String, Long> validationCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;

    // Track verification attempts for security monitoring
    private final AtomicInteger successfulVerifications = new AtomicInteger(0);
    private final AtomicInteger failedVerifications = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> failuresByIP = new ConcurrentHashMap<>();

    // Maximum length for fields to prevent DoS attacks
    private static final int MAX_FIELD_LENGTH = 2048;

    public PalmpayVerifySignUtil() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Verify signature for Palmpay webhook callback with additional security measures
     * @param paramBodyJson The raw JSON string from the webhook
     * @param publicKey Palmpay's public key
     * @param signType The signature type (RSA or HMACSHA256)
     * @return true if signature is valid
     */
    public boolean verifySignForCallback(String paramBodyJson, String publicKey, EaseIdSignUtil.SignType signType) throws Exception {
        // Input validation
        if (paramBodyJson == null || paramBodyJson.isEmpty() || publicKey == null || publicKey.isEmpty()) {
            log.warn("Invalid input for signature verification: empty JSON or public key");
            failedVerifications.incrementAndGet();
            return false;
        }

        // Check for excessively large payloads to prevent DoS attacks
        if (paramBodyJson.length() > 32768) { // 32KB limit
            log.warn("Payload too large for signature verification: {} bytes", paramBodyJson.length());
            failedVerifications.incrementAndGet();
            return false;
        }

        // Check for replay attacks using the validation cache
        String payloadHash;
        try {
            payloadHash = generatePayloadHash(paramBodyJson);

            // Check if this exact payload was already processed
            if (validationCache.containsKey(payloadHash)) {
                Long timestamp = validationCache.get(payloadHash);
                // Allow reprocessing after 24 hours (in case of legitimate retries)
                if (System.currentTimeMillis() - timestamp < 86400000) {
                    log.warn("Potential replay attack detected: duplicate payload");
                    failedVerifications.incrementAndGet();
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Error generating payload hash: {}", e.getMessage());
            failedVerifications.incrementAndGet();
            return false;
        }

        try {
            // Parse the JSON string to a Map with security checks
            Map<String, String> data;
            try {
                data = objectMapper.readValue(paramBodyJson, new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                log.error("Error parsing JSON payload: {}", e.getMessage());
                failedVerifications.incrementAndGet();
                return false;
            }

            // Validate field lengths to prevent memory exhaustion
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry.getKey().length() > MAX_FIELD_LENGTH ||
                        (entry.getValue() != null && entry.getValue().length() > MAX_FIELD_LENGTH)) {
                    log.warn("Field too large in payload: {} (key length: {}, value length: {})",
                            entry.getKey(), entry.getKey().length(),
                            entry.getValue() == null ? 0 : entry.getValue().length());
                    failedVerifications.incrementAndGet();
                    return false;
                }
            }

            Set<String> set = data.keySet();
            if (EaseIdSignUtil.SignType.RSA.equals(signType)) {
                for (String key : set) {
                    data.put(key, String.valueOf(data.get(key)));
                }
            }

            // Extract and decode the signature with security checks
            String sign = data.get("sign");
            if (sign == null || sign.isEmpty()) {
                log.warn("Missing signature in payload");
                failedVerifications.incrementAndGet();
                return false;
            }

            // Security-enhanced URL decoding with proper character encoding
            try {
                sign = URLDecoder.decode(sign, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                log.error("Error decoding signature: {}", e.getMessage());
                failedVerifications.incrementAndGet();
                return false;
            }

            // Remove signature from data to verify
            data.remove("sign");

            // Verify the signature
            boolean isValid = verifySignature(data, publicKey, sign, signType);

            // Update statistics
            if (isValid) {
                successfulVerifications.incrementAndGet();
                // Cache the validated payload to prevent replay attacks
                if (validationCache.size() >= MAX_CACHE_SIZE) {
                    // Cleanup old entries if cache is full
                    cleanupValidationCache();
                }
                validationCache.put(payloadHash, System.currentTimeMillis());
            } else {
                failedVerifications.incrementAndGet();
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage());
            failedVerifications.incrementAndGet();
            throw e;
        }
    }

    /**
     * Verify signature with more robust error handling
     * @param data Map of data to verify
     * @param publicKey Public key for verification
     * @param sign Signature to verify
     * @param signType Type of signature
     * @return true if signature is valid
     */
    private boolean verifySignature(final Map<String, String> data, String publicKey, String sign, EaseIdSignUtil.SignType signType) {
        try {
            String encryData = sortStr(data);
            if (EaseIdSignUtil.SignType.RSA.equals(signType)) {
                return EaseIdRsaUtil.verify(encryData, publicKey, sign);
            } else {
                return EaseIdRsaUtil.HMACSHA256(encryData, publicKey).equals(sign);
            }
        } catch (Exception e) {
            log.error("Error in signature verification process: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sort parameters alphabetically and create a string for signature verification
     * with improved security and error handling
     * @param data Map of parameters to sort
     * @return MD5 hash of the sorted parameter string
     */
    private String sortStr(final Map<String, String> data) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new InvalidParameterException("Empty or null data for signature calculation");
        }

        Set<String> keySet = data.keySet();
        String[] keyArray = keySet.toArray(new String[0]);
        Arrays.sort(keyArray);

        StringBuilder sb = new StringBuilder();
        for (String k : keyArray) {
            String value = data.get(k);
            if (value != null && value.trim().length() > 0) { // Do not Skip empty values
                sb.append(k).append("=").append(value).append("&");

            }
        }

        // Safely handle empty parameter string
        if (sb.length() == 0) {
            throw new InvalidParameterException("No valid parameters found for signature calculation");
        }

        // Remove trailing & character
        String encryData = sb.substring(0, sb.length()-1);

        // Generate MD5 hash
        try {
            return EaseIdMd5Util.MD5(encryData);
        } catch (Exception e) {
            log.error("Error generating MD5 hash: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Generate a hash of the payload for replay attack detection
     * @param payload The payload to hash
     * @return A hash string representing the payload
     */
    private String generatePayloadHash(String payload) throws Exception {
        return EaseIdMd5Util.MD5(payload);
    }

    /**
     * Clean up old entries from the validation cache
     */
    private void cleanupValidationCache() {
        // Remove entries older than 24 hours
        long cutoffTime = System.currentTimeMillis() - 86400000;
        validationCache.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    /**
     * Get verification statistics for monitoring
     * @return Map containing verification statistics
     */
    public Map<String, Object> getVerificationStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("successfulVerifications", successfulVerifications.get());
        stats.put("failedVerifications", failedVerifications.get());
        stats.put("cacheSize", validationCache.size());
        return stats;
    }

    /**
     * Reset verification statistics (for testing or monitoring)
     */
    public void resetStats() {
        successfulVerifications.set(0);
        failedVerifications.set(0);
        failuresByIP.clear();
    }
}