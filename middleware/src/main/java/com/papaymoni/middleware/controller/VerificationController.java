package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.EmailVerificationDtos.EmailVerificationRequestDto;
import com.papaymoni.middleware.dto.EmailVerificationDtos.OtpVerificationRequestDto;
import com.papaymoni.middleware.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * Send email verification OTP
     * @param requestDto email verification request
     * @return response with success or error
     */
    @PostMapping("/email/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationOtp(
            @Valid @RequestBody EmailVerificationRequestDto requestDto) {

        log.info("Received request to send email verification OTP: {}", requestDto.getEmail());

        boolean sent = emailVerificationService.sendOtp(requestDto.getEmail());

        if (sent) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Verification OTP sent to your email", null));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to send verification OTP", null));
        }
    }

    /**
     * Verify email with OTP
     * @param requestDto OTP verification request
     * @return response with success or error
     */
    @PostMapping("/email/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyEmailOtp(
            @Valid @RequestBody OtpVerificationRequestDto requestDto) {

        log.info("Received request to verify email OTP: {}", requestDto.getEmail());

        boolean verified = emailVerificationService.verifyOtp(
                requestDto.getEmail(), requestDto.getOtp());

        if (verified) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Email verified successfully", null));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid or expired OTP", null));
        }
    }

    /**
     * Check email verification status
     * @param email the email to check
     * @return response with verification status
     */
    @GetMapping("/email/status")
    public ResponseEntity<ApiResponse<Boolean>> checkEmailVerificationStatus(
            @RequestParam String email) {

        log.info("Checking email verification status: {}", email);

        boolean isVerified = emailVerificationService.isEmailVerified(email);

        return ResponseEntity.ok(ApiResponse.success(
                isVerified ? "Email is verified" : "Email is not verified",
                isVerified));
    }
}