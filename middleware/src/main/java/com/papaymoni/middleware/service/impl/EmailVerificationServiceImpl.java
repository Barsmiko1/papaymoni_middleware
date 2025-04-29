package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.model.EmailVerification;
import com.papaymoni.middleware.repository.EmailVerificationRepository;
import com.papaymoni.middleware.service.EmailService;
import com.papaymoni.middleware.service.EmailVerificationService;
import com.papaymoni.middleware.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;

    /**
     * Generate and send OTP to the provided email
     * @param email the email to send OTP to
     * @return true if OTP was sent successfully
     */
    @Override
    @Transactional
    public boolean sendOtp(String email) {
        log.info("Sending OTP to email: {}", email);

        // Generate 6-digit OTP
        String otp = generateOtp();

        // Save or update verification record
        Optional<EmailVerification> existingVerification = emailVerificationRepository.findByEmailAndVerifiedFalse(email);

        EmailVerification verification;
        if (existingVerification.isPresent()) {
            verification = existingVerification.get();
            verification.setOtp(otp);
            verification.setExpiryTime(LocalDateTime.now().plusMinutes(15));
        } else {
            verification = new EmailVerification();
            verification.setEmail(email);
            verification.setOtp(otp);
            verification.setVerified(false);
        }

        emailVerificationRepository.save(verification);

        // Send OTP via email
        String subject = "Papay Moni - Email Verification";
        String htmlBody = "<div style='font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                "<h2 style='color: #333; text-align: center;'>Email Verification</h2>" +
                "<p>Thank you for registering with Papay Moni. Please use the following OTP to verify your email address:</p>" +
                "<div style='background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 5px; margin: 20px 0;'>" +
                otp +
                "</div>" +
                "<p>This OTP will expire in 15 minutes.</p>" +
                "<p>If you did not request this verification, please ignore this email.</p>" +
                "<p style='margin-top: 30px; font-size: 12px; color: #777; text-align: center;'>© " +
                LocalDateTime.now().getYear() +
                " Papay Moni. All rights reserved.</p>" +
                "</div>";

        boolean sent = emailService.sendHtmlMessage(email, subject, htmlBody);

        if (sent) {
            log.info("OTP sent successfully to email: {}", email);
        } else {
            log.error("Failed to send OTP to email: {}", email);
        }

        return sent;
    }

    /**
     * Verify OTP for the provided email
     * @param email the email to verify
     * @param otp the OTP to verify
     * @return true if OTP is valid
     */
    @Override
    @Transactional
    public boolean verifyOtp(String email, String otp) {
        log.info("Verifying OTP for email: {}", email);

        Optional<EmailVerification> verificationOpt = emailVerificationRepository.findByEmailAndVerifiedFalse(email);

        if (!verificationOpt.isPresent()) {
            log.warn("No pending verification found for email: {}", email);
            return false;
        }

        EmailVerification verification = verificationOpt.get();

        // Check if OTP is expired
        if (verification.isExpired()) {
            log.warn("OTP has expired for email: {}", email);
            return false;
        }

        // Check if OTP matches
        if (!verification.getOtp().equals(otp)) {
            log.warn("Invalid OTP provided for email: {}", email);
            return false;
        }

        // Mark as verified
        verification.setVerified(true);
        verification.setVerifiedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        log.info("Email verified successfully: {}", email);
        return true;
    }

    /**
     * Check if email is already verified
     * @param email the email to check
     * @return true if email is verified
     */
    @Override
    public boolean isEmailVerified(String email) {
        return emailVerificationRepository.existsByEmailAndVerifiedTrue(email);
    }

    /**
     * Generate a random 6-digit OTP
     * @return the generated OTP
     */
    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // 6-digit OTP
        return String.valueOf(otp);
    }
}