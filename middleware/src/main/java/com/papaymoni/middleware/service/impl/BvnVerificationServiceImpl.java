package com.papaymoni.middleware.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.BvnVerificationResultDto;
import com.papaymoni.middleware.model.BvnVerification;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.BvnVerificationRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.BvnVerificationService;
import com.papaymoni.middleware.service.EncryptionService;
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
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class BvnVerificationServiceImpl implements BvnVerificationService {

    private final BvnVerificationRepository bvnVerificationRepository;
    private final UserRepository userRepository;
    private final BvnResponseProcessor responseProcessor;
    private final EncryptionService encryptionService;
    private final CacheManager cacheManager;
    private final CloseableHttpClient httpClient;

    private static final String CACHE_NAME = "bvnVerification";

    @Value("${bvn.verification.api.url}")
    private String bvnVerificationApiUrl;

    @Value("${bvn.verification.api.key}")
    private String apiKey;

    @Value("${bvn.verification.private.key}")
    private String privateKeyBase64;

    @Value("${app.name:PapaymoniApp}")
    private String appName;

    @Value("${app.version}")
    private String appVersion;

    public BvnVerificationServiceImpl(BvnVerificationRepository bvnVerificationRepository,
                                      UserRepository userRepository,
                                      BvnResponseProcessor responseProcessor,
                                      EncryptionService encryptionService,
                                      CacheManager cacheManager) {
        this.bvnVerificationRepository = bvnVerificationRepository;
        this.userRepository = userRepository;
        this.responseProcessor = responseProcessor;
        this.encryptionService = encryptionService;
        this.cacheManager = cacheManager;

        // Configure HTTP client with optimized connection pooling
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(30000)
                .setConnectionRequestTimeout(10000)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Override
    @CachePut(value = CACHE_NAME, key = "#bvn")
    public BvnVerificationResultDto verifyBvn(
            String bvn, String firstName, String lastName,
            LocalDate dateOfBirth, String gender) {

        log.info("Verifying BVN: {} for user: {} {}", bvn, firstName, lastName);

        try {
            // Check if already verified (from cache)
            if (isBvnVerified(bvn)) {
                log.info("BVN already verified: {}", bvn);
                return BvnVerificationResultDto.builder()
                        .verified(true)
                        .firstNameMatch(true)
                        .lastNameMatch(true)
                        .dateOfBirthMatch(true)
                        .genderMatch(true)
                        .message("BVN already verified")
                        .responseCode("00")
                        .build();
            }

            // Prepare request payload
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("version", "V1.1");
            requestParams.put("requestTime", System.currentTimeMillis());
            requestParams.put("nonceStr", generateNonce());
            requestParams.put("bvn", bvn);

            // Generate signature
            String signature = EaseIdSignUtil.generateSign(requestParams, privateKeyBase64,
                    EaseIdSignUtil.SignType.RSA);

            // Make API call
            String responseBody = sendBvnVerificationRequest(requestParams, signature);

            // Process the response
            BvnVerificationResultDto result = responseProcessor.processBvnResponse(
                    responseBody, firstName, lastName, dateOfBirth, gender, bvn);

            // Save verification result if successful
            if (result.isVerified()) {
                saveVerificationResult(bvn, firstName, lastName, dateOfBirth.toString(), gender);

                // Update user's BVN verification status if exist
                updateUserBvnStatus(bvn, true);
            }

            return result;
        } catch (Exception e) {
            log.error("Error verifying BVN: {}", e.getMessage(), e);
            return BvnVerificationResultDto.builder()
                    .verified(false)
                    .message("Error verifying BVN: " + e.getMessage())
                    .responseCode("99")
                    .build();
        }
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'verified:' + #bvn")
    public boolean isBvnVerified(String bvn) {
        // First check cache
        Boolean cached = cacheManager.getCache(CACHE_NAME).get(bvn, Boolean.class);
        if (cached != null) {
            return cached;
        }

        // Then check database - try with encrypted BVN if encryption service is available
        boolean verified = false;
        try {
            String encryptedBvn = encryptionService.encrypt(bvn);
            verified = bvnVerificationRepository.existsByBvnAndVerifiedTrue(encryptedBvn);
        } catch (Exception e) {
            // Fall back to direct check if encryption fails
            verified = bvnVerificationRepository.existsByBvnAndVerifiedTrue(bvn);
        }

        // Update cache
        cacheManager.getCache(CACHE_NAME).put(bvn, verified);

        return verified;
    }

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

    private String sendBvnVerificationRequest(Map<String, Object> requestParams, String signature)
            throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String requestBody;

        try {
            requestBody = mapper.writeValueAsString(requestParams);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request params to JSON", e);
            throw new IOException("Failed to serialize request parameters", e);
        }

        HttpPost httpPost = new HttpPost(bvnVerificationApiUrl);

        // Set headers
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("CountryCode", "NG");
        httpPost.setHeader("Signature", signature);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("User-Agent", appName + "/" + appVersion + " Java/" + System.getProperty("java.version"));

        // Set request body
        try {
            StringEntity entity = new StringEntity(requestBody, "UTF-8");
            httpPost.setEntity(entity);
        } catch (Exception e) {
            log.error("Encoding error when creating request entity", e);
            throw new IOException("Failed to create request entity", e);
        }

        // Execute request with improved error handling
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity responseEntity = response.getEntity();

            String responseBody;
            try {
                responseBody = EntityUtils.toString(responseEntity, "UTF-8");
            } catch (IOException e) {
                log.error("Failed to read response body", e);
                throw new IOException("Failed to read response from BVN API", e);
            }

            log.debug("BVN API response status: {}", statusCode);

            if (statusCode >= 400) {
                log.error("BVN verification API error: {} - {}", statusCode, responseBody);
                throw new IOException("BVN verification API returned error: " + statusCode + " - " + responseBody);
            }

            return responseBody;
        } catch (IOException e) {
            log.error("HTTP client error during BVN verification", e);
            throw e;
        }
    }

    @Transactional
    private void saveVerificationResult(String bvn, String firstName, String lastName, String dob, String gender) {
        try {
            BvnVerification verification = new BvnVerification();

            // Encrypt BVN before saving
            try {
                verification.setBvn(encryptionService.encrypt(bvn));
            } catch (Exception e) {
                log.warn("Failed to encrypt BVN, saving without encryption", e);
                verification.setBvn(bvn);
            }

            verification.setFirstName(firstName);
            verification.setLastName(lastName);
            verification.setDateOfBirth(dob);
            verification.setGender(gender);
            verification.setVerified(true);
            verification.setVerifiedAt(LocalDateTime.now());

            bvnVerificationRepository.save(verification);

            // Update cache
            cacheManager.getCache(CACHE_NAME).put(bvn, true);
            cacheManager.getCache(CACHE_NAME).put("verified:" + bvn, true);

            log.info("BVN verification result saved to database for BVN: {}", bvn);
        } catch (Exception e) {
            log.error("Error saving BVN verification result: {}", e.getMessage(), e);
        }
    }

    @Transactional
    private void updateUserBvnStatus(String bvn, boolean status) {
        try {
            // Find user by BVN
            String encryptedBvn = null;
            try {
                encryptedBvn = encryptionService.encrypt(bvn);
            } catch (Exception e) {
                log.warn("Failed to encrypt BVN for lookup", e);
                encryptedBvn = bvn;
            }

            Optional<User> userOpt = userRepository.findByBvn(encryptedBvn);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setBvnVerified(status);
                userRepository.save(user);

                // Update user cache
                cacheManager.getCache("users").put(user.getId(), user);
                cacheManager.getCache("users").put(user.getUsername(), user);
                cacheManager.getCache("users").put(user.getEmail(), user);

                log.info("Updated BVN verification status for user: {} to: {}",
                        user.getUsername(), status);
            }
        } catch (Exception e) {
            log.error("Error updating user BVN status: {}", e.getMessage(), e);
        }
    }
}