package com.papaymoni.middleware.controller;


import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.BvnVerificationRequestDto;
import com.papaymoni.middleware.dto.BvnVerificationResultDto;
import com.papaymoni.middleware.dto.BvnVerificationStandaloneRequestDto;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.BvnVerificationService;
import com.papaymoni.middleware.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/verification/bvn")
@RequiredArgsConstructor
public class BvnVerificationController {

    private final BvnVerificationService bvnVerificationService;
    private final UserService userService;

    /**
     * Verify BVN for authenticated user
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<BvnVerificationResultDto>> verifyBvn(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody BvnVerificationRequestDto verificationRequest) {

        try {
            log.info("Processing BVN verification for user: {}", currentUser.getUsername());

            // Get current user
            User user = userService.getUserByUsername(currentUser.getUsername());

            // Perform BVN verification
            BvnVerificationResultDto result = bvnVerificationService.verifyBvn(
                    verificationRequest.getBvn(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getDateOfBirth(),
                    user.getGender()
            );

            if (result.isVerified()) {
                log.info("BVN verification successful for user: {}", currentUser.getUsername());
                return ResponseEntity.ok(ApiResponse.success("BVN verification successful", result));
            } else {
                log.warn("BVN verification failed for user: {} - {}", currentUser.getUsername(), result.getMessage());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("BVN verification failed: " + result.getMessage(), result));
            }

        } catch (Exception e) {
            log.error("Error during BVN verification for user: {}", currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("BVN verification failed due to an error: " + e.getMessage()));
        }
    }

    /**
     * Check if user's BVN is already verified
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Boolean>> getBvnVerificationStatus(
            @AuthenticationPrincipal UserDetails currentUser) {

        try {
            log.info("Checking BVN verification status for user: {}", currentUser.getUsername());

            User user = userService.getUserByUsername(currentUser.getUsername());

            boolean isVerified = user.isBvnVerified();

            return ResponseEntity.ok(ApiResponse.success(
                    isVerified ? "BVN is verified" : "BVN is not verified",
                    isVerified));

        } catch (Exception e) {
            log.error("Error checking BVN verification status for user: {}", currentUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check BVN verification status: " + e.getMessage()));
        }
    }

    /**
     * Standalone BVN verification (admin or specific use cases)
     */
    @PostMapping("/verify-standalone")
    public ResponseEntity<ApiResponse<BvnVerificationResultDto>> verifyBvnStandalone(
            @Valid @RequestBody BvnVerificationStandaloneRequestDto verificationRequest) {

        try {
            log.info("Processing standalone BVN verification for BVN: {}",
                    verificationRequest.getBvn().substring(0, 4) + "****");

            BvnVerificationResultDto result = bvnVerificationService.verifyBvn(
                    verificationRequest.getBvn(),
                    verificationRequest.getFirstName(),
                    verificationRequest.getLastName(),
                    verificationRequest.getDateOfBirth(),
                    verificationRequest.getGender()
            );

            if (result.isVerified()) {
                log.info("Standalone BVN verification successful");
                return ResponseEntity.ok(ApiResponse.success("BVN verification successful", result));
            } else {
                log.warn("Standalone BVN verification failed - {}", result.getMessage());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("BVN verification failed: " + result.getMessage(), result));
            }

        } catch (Exception e) {
            log.error("Error during standalone BVN verification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("BVN verification failed due to an error: " + e.getMessage()));
        }
    }
}
