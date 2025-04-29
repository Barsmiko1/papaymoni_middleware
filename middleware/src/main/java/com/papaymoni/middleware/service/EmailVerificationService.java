package com.papaymoni.middleware.service;

public interface EmailVerificationService {
    /**
     * Generate and send OTP to the provided email
     * @param email the email to send OTP to
     * @return true if OTP was sent successfully
     */
    boolean sendOtp(String email);

    /**
     * Verify OTP for the provided email
     * @param email the email to verify
     * @param otp the OTP to verify
     * @return true if OTP is valid
     */
    boolean verifyOtp(String email, String otp);

    /**
     * Check if email is already verified
     * @param email the email to check
     * @return true if email is verified
     */
    boolean isEmailVerified(String email);
}