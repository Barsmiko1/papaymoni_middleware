package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.PasswordResetDto;

public interface PasswordResetService {
    /**
     * Initiates the password reset process by sending a reset link to the user's email
     *
     * @param email the user's email
     * @return ApiResponse indicating success or failure
     */
    ApiResponse<Void> initiatePasswordReset(String email);

    /**
     * Validates a password reset token
     * @param token the reset token
     * @return ApiResponse indicating if the token is valid
     */
    ApiResponse<Boolean> validateResetToken(String token);

    /**
     * Completes the password reset process by updating the user's password
     *
     * @param resetDto the reset request containing token and new password
     * @return ApiResponse indicating success or failure
     */
    ApiResponse<Void> resetPassword(PasswordResetDto.ResetDto resetDto);
}
