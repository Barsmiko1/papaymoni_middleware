package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.BvnVerificationResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Helper component to process BVN API responses
 */
@Slf4j
@Component
public class BvnResponseProcessor {

    private final ObjectMapper objectMapper;

    public BvnResponseProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Process the BVN verification API response and convert to DTO
     *
     * @param responseBody API response body
     * @param firstName User's first name to match
     * @param lastName User's last name to match
     * @param dateOfBirth User's date of birth to match
     * @param gender User's gender to match
     * @param bvn BVN being verified
     * @return BvnVerificationResultDto with match results
     */
    public BvnVerificationResultDto processBvnResponse(
            String responseBody, String firstName, String lastName,
            LocalDate dateOfBirth, String gender, String bvn) {

        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);

            // Check if the API returned a special respCode
            if (responseNode.has("respCode")) {
                String respCode = responseNode.get("respCode").asText();
                String respMsg = responseNode.has("respMsg") ? responseNode.get("respMsg").asText() : "Unknown error";

                log.info("BVN verification API returned respCode: {}, respMsg: {}", respCode, respMsg);

                // SUCCESS CASE - respCode 00000000 means success!
                if ("00000000".equals(respCode) && responseNode.has("data")) {
                    // Process successful API response with data
                    JsonNode data = responseNode.get("data");

                    String apiBvn = data.has("bvn") ? data.get("bvn").asText() : "";
                    String apiFirstName = data.has("firstName") ? data.get("firstName").asText() : "";
                    String apiLastName = data.has("lastName") ? data.get("lastName").asText() : "";
                    String apiDobStr = data.has("birthday") ? data.get("birthday").asText() : "";
                    String apiGender = data.has("gender") ? data.get("gender").asText() : "";

                    // Check BVN match
                    boolean bvnMatch = bvn.equalsIgnoreCase(apiBvn);

                    // Check if details match (case-insensitive)
                    boolean firstNameMatch = apiFirstName.equalsIgnoreCase(firstName);
                    boolean lastNameMatch = apiLastName.equalsIgnoreCase(lastName);

                    // Parse and compare date of birth
                    boolean dobMatch = false;
                    if (!apiDobStr.isEmpty()) {
                        try {
                            LocalDate apiDob = LocalDate.parse(apiDobStr, DateTimeFormatter.ISO_DATE);
                            dobMatch = apiDob.equals(dateOfBirth);
                        } catch (Exception e) {
                            log.error("Error parsing API date of birth: {}", apiDobStr, e);
                        }
                    }

                    // Check gender match
                    boolean genderMatch = apiGender.equalsIgnoreCase(gender);

                    // All match = verification is successful
                    boolean allMatch = firstNameMatch && lastNameMatch && dobMatch && genderMatch;

                    log.info("BVN verification matches: BVN={}, firstName={}, lastName={}, dob={}, gender={}, allMatch={}",
                            bvnMatch, firstNameMatch, lastNameMatch, dobMatch, genderMatch, allMatch);

                    return BvnVerificationResultDto.builder()
                            .verified(allMatch)
                            .firstNameMatch(firstNameMatch)
                            .lastNameMatch(lastNameMatch)
                            .dateOfBirthMatch(dobMatch)
                            .genderMatch(genderMatch)
                            .message(allMatch ? "BVN verification successful" : "BVN details do not match")
                            .responseCode("00")
                            .build();
                } else {
                    // For non-success API responses
                    return BvnVerificationResultDto.builder()
                            .verified(false)
                            .message("BVN verification API response: " + respCode + " - " + respMsg)
                            .responseCode(respCode)
                            .build();
                }
            } else if (responseNode.has("responseCode")) {
                // Handle different response format
                String responseCode = responseNode.get("responseCode").asText();
                String responseMessage = responseNode.has("responseMessage") ?
                        responseNode.get("responseMessage").asText() : "Unknown response format";

                return BvnVerificationResultDto.builder()
                        .verified("00".equals(responseCode))
                        .message(responseMessage)
                        .responseCode(responseCode)
                        .build();
            } else {
                // Unknown response format
                log.error("Unexpected BVN verification API response format: {}", responseBody);
                return BvnVerificationResultDto.builder()
                        .verified(false)
                        .message("Unexpected BVN verification API response format")
                        .responseCode("99")
                        .build();
            }
        } catch (Exception e) {
            log.error("Error processing BVN verification response: {}", e.getMessage(), e);
            return BvnVerificationResultDto.builder()
                    .verified(false)
                    .message("Error processing BVN verification response: " + e.getMessage())
                    .responseCode("99")
                    .build();
        }
    }
}