package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.BvnVerificationResultDto;
import com.papaymoni.middleware.model.BvnVerification;
import com.papaymoni.middleware.repository.BvnVerificationRepository;
import com.papaymoni.middleware.service.BvnVerificationService;
import com.papaymoni.middleware.util.EaseIdSignUtil;
import com.papaymoni.middleware.util.EaseIdSignUtil.SignType;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class BvnVerificationServiceImpl implements BvnVerificationService {

    private final BvnVerificationRepository bvnVerificationRepository;
    private final ObjectMapper objectMapper;
    private final BvnVerificationStatusUpdater statusUpdater;
    private final CloseableHttpClient httpClient;
    private final BvnResponseProcessor responseProcessor;

    @Value("${bvn.verification.api.url:https://open-api.easeid.ai/api/validator-service/open/bvn/inquire}")
    private String bvnVerificationApiUrl;

    @Value("${bvn.verification.api.key:K7657831424}")
    private String apiKey;

    @Value("${bvn.verification.private.key}")
    private String privateKeyBase64;

    @Value("${app.name:PapaymoniApp}")
    private String appName;

    @Value("${app.version:1.0}")
    private String appVersion;

    private PrivateKey privateKey;

    public BvnVerificationServiceImpl(BvnVerificationRepository bvnVerificationRepository,
                                      ObjectMapper objectMapper,
                                      BvnVerificationStatusUpdater statusUpdater,
                                      BvnResponseProcessor responseProcessor) {
        this.bvnVerificationRepository = bvnVerificationRepository;
        this.objectMapper = objectMapper;
        this.statusUpdater = statusUpdater;
        this.responseProcessor = responseProcessor;
        this.httpClient = HttpClients.createDefault();
    }

    @PostConstruct
    public void init() {
        try {
            // Check if we have a private key
            if (privateKeyBase64 != null && !privateKeyBase64.isEmpty()) {
                byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKeyBase64);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                privateKey = keyFactory.generatePrivate(keySpec);
                log.info("BVN verification private key loaded successfully");
            } else {
                log.warn("No BVN verification private key provided. Signature generation will fail.");
            }
        } catch (Exception e) {
            log.error("Error initializing BVN verification service: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate a 32-byte random hexadecimal string for nonce
     * @return 32-byte hex string
     */
    private String generateNonceStr() {
        byte[] randomBytes = new byte[16]; // 16 bytes will convert to 32 hex characters
        new SecureRandom().nextBytes(randomBytes);
        return bytesToHex(randomBytes);
    }

    /**
     * Convert byte array to hexadecimal string
     * @param bytes byte array to convert
     * @return hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Verify BVN details against the provided information
     * @param bvn the BVN to verify
     * @param firstName the first name to match
     * @param lastName the last name to match
     * @param dateOfBirth the date of birth to match
     * @param gender the gender to match
     * @return verification result with match details
     */
    @Override
    @Transactional
    public BvnVerificationResultDto verifyBvn(
            String bvn, String firstName, String lastName,
            LocalDate dateOfBirth, String gender) {

        log.info("Verifying BVN: {} for user: {} {}", bvn, firstName, lastName);

        try {
            // Check if already verified
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

            // Prepare request payload with dynamic values
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("version", "V1.1");
            requestParams.put("requestTime", new Date().getTime());
            requestParams.put("nonceStr", generateNonceStr());
            requestParams.put("bvn", bvn);

            log.info("BVN verification payload: {}", requestParams);

            // Generate signature using EaseIdSignUtil with the fixed implementation
            String signature;
            try {
                // Use the updated EaseIdSignUtil with PalmPay's approach
                signature = EaseIdSignUtil.generateSign(requestParams, privateKeyBase64, SignType.RSA);
                log.info("Generated EaseID signature for BVN verification");

                // Format parameter string for debugging
                StringBuilder paramString = new StringBuilder();
                List<String> keys = new ArrayList<>(requestParams.keySet());
                Collections.sort(keys);
                boolean first = true;
                for (String key : keys) {
                    if (!first) {
                        paramString.append("&");
                    }
                    paramString.append(key).append("=").append(requestParams.get(key));
                    first = false;
                }
                log.debug("Parameter string used for signature: {}", paramString.toString());
                log.debug("Generated signature: {}", signature);
            } catch (Exception e) {
                log.error("Failed to generate EaseID signature: {}", e.getMessage(), e);
                return BvnVerificationResultDto.builder()
                        .verified(false)
                        .message("Failed to generate signature: " + e.getMessage())
                        .responseCode("99")
                        .build();
            }

            // Make API call
            try {
                String responseBody = sendBvnVerificationRequest(requestParams, signature);

                // Process the response using the dedicated processor
                BvnVerificationResultDto result = responseProcessor.processBvnResponse(
                        responseBody, firstName, lastName, dateOfBirth, gender, bvn);

                // Save verification result if successful
                if (result.isVerified()) {
                    saveVerificationResult(bvn, firstName, lastName, dateOfBirth.toString(), gender);

                    // Update user's BVN verification status if they exist
                    statusUpdater.updateBvnVerificationStatus(bvn, result);
                }

                return result;
            } catch (Exception e) {
                log.error("Error during BVN verification API call: {}", e.getMessage(), e);
                return BvnVerificationResultDto.builder()
                        .verified(false)
                        .message("BVN verification error: " + e.getMessage())
                        .responseCode("99")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error verifying BVN: {}", e.getMessage(), e);
            return BvnVerificationResultDto.builder()
                    .verified(false)
                    .message("Error verifying BVN: " + e.getMessage())
                    .responseCode("99")
                    .build();
        }
    }

    /**
     * Save the BVN verification result to the database
     */
    private void saveVerificationResult(String bvn, String firstName, String lastName, String dob, String gender) {
        try {
            BvnVerification verification = new BvnVerification();
            verification.setBvn(bvn);
            verification.setFirstName(firstName);
            verification.setLastName(lastName);
            verification.setDateOfBirth(dob);
            verification.setGender(gender);
            verification.setVerified(true);
            bvnVerificationRepository.save(verification);
            log.info("BVN verification result saved to database for BVN: {}", bvn);
        } catch (Exception e) {
            log.error("Error saving BVN verification result: {}", e.getMessage(), e);
            // Continue processing even if save fails
        }
    }

    /**
     * Test method for verifying BVN with diagnostic logging
     * @param bvn the BVN to verify
     * @return verification result
     */
    public BvnVerificationResultDto diagnosticVerifyBvn(String bvn) {
        log.info("Running diagnostic BVN verification for BVN: {}", bvn);

        try {
            // Prepare request payload with dynamic values
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("version", "V1.1");
            requestParams.put("requestTime", new Date().getTime());
            requestParams.put("nonceStr", generateNonceStr());
            requestParams.put("bvn", bvn);

            log.info("Diagnostic BVN verification payload: {}", requestParams);

            // Generate signature
            String signature = EaseIdSignUtil.generateSign(requestParams, privateKeyBase64, SignType.RSA);

            // Make API call
            String responseBody = sendBvnVerificationRequest(requestParams, signature);
            log.info("Diagnostic BVN verification response: {}", responseBody);

            return BvnVerificationResultDto.builder()
                    .verified(true)
                    .message("Diagnostic test completed successfully")
                    .responseCode("00")
                    .build();

        } catch (Exception e) {
            log.error("Error in diagnostic BVN verification: {}", e.getMessage(), e);
            return BvnVerificationResultDto.builder()
                    .verified(false)
                    .message("Diagnostic test failed: " + e.getMessage())
                    .responseCode("99")
                    .build();
        }
    }

    /**
     * Send BVN verification request using Apache HttpClient
     *
     * @param requestParams The request parameters
     * @param signature The generated signature
     * @return The response body as string
     * @throws Exception If an error occurs during the request
     */
    private String sendBvnVerificationRequest(Map<String, Object> requestParams, String signature) throws Exception {
        // Create HTTP POST request
        HttpPost httpPost = new HttpPost(bvnVerificationApiUrl);

        // Set headers
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("CountryCode", "NG");
        httpPost.setHeader("Signature", signature);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);

        // Set a generic User-Agent - this is important to avoid 403 errors
        httpPost.setHeader("User-Agent", appName + "/" + appVersion + " Java/" + System.getProperty("java.version"));

        // Convert request params to JSON string for the request body
        String requestBody = objectMapper.writeValueAsString(requestParams);

        // Create request entity
        StringEntity entity = new StringEntity(requestBody);
        httpPost.setEntity(entity);

        // Execute the request
        log.info("Making BVN verification request to: {}", bvnVerificationApiUrl);
        log.debug("Request body: {}", requestBody);

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            // Check response status
            int statusCode = response.getStatusLine().getStatusCode();
            log.info("BVN verification API response status: {}", statusCode);

            // Get response body
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);
            log.debug("Response body: {}", responseBody);

            // Check for error status codes
            if (statusCode >= 400) {
                log.error("BVN verification API error: {} - {}", statusCode, responseBody);
                throw new IOException("BVN verification API returned error: " + statusCode + " - " + responseBody);
            }

            return responseBody;
        }
    }

    /**
     * Check if BVN is already verified
     * @param bvn the BVN to check
     * @return true if BVN is verified
     */
    @Override
    public boolean isBvnVerified(String bvn) {
        return bvnVerificationRepository.existsByBvnAndVerifiedTrue(bvn);
    }
}