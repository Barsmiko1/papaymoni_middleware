package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.PasswordResetDto;
import com.papaymoni.middleware.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/password")
@Slf4j
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody PasswordResetDto.RequestDto requestDto) {
        log.info("Received forgot password request for email: {}", requestDto.getEmail());
        ApiResponse<Void> response = passwordResetService.initiatePasswordReset(requestDto.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate-token")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @Valid @RequestBody PasswordResetDto.TokenValidationDto validationDto) {
        log.info("Validating password reset token");
        ApiResponse<Boolean> response = passwordResetService.validateResetToken(validationDto.getToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetDto.ResetDto resetDto) {
        log.info("Processing password reset");
        ApiResponse<Void> response = passwordResetService.resetPassword(resetDto);
        return ResponseEntity.ok(response);
    }
}
