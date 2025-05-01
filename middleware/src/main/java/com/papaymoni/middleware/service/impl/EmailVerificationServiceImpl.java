package com.papaymoni.middleware.service.impl;
import com.papaymoni.middleware.model.EmailVerification;
import com.papaymoni.middleware.repository.EmailVerificationRepository;
import com.papaymoni.middleware.service.EmailService;
import com.papaymoni.middleware.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import javax.transaction.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final CacheManager cacheManager;

    // Cache name for email verification status
    private static final String VERIFICATION_CACHE = "emailVerification";

    // Cache name for verification attempts (for rate limiting)
    private static final String ATTEMPTS_CACHE = "verificationAttempts";

    @Override
    @Transactional
    public boolean sendOtp(String email) {
        log.info("Sending OTP to email: {}", email);

        // Check rate limiting (no more than 3 attempts in 15 minutes)
        Integer attempts = cacheManager.getCache(ATTEMPTS_CACHE).get(email, Integer.class);
        if (attempts != null && attempts >= 3) {
            log.warn("Rate limit exceeded for email: {}", email);
            return false;
        }

        // Generate 6-digit OTP
        String otp = generateOtp();

        // Update attempt counter
        cacheManager.getCache(ATTEMPTS_CACHE).put(email,
                attempts == null ? 1 : attempts + 1);

        // Save or update verification record
        Optional<EmailVerification> existingVerification =
                emailVerificationRepository.findByEmailAndVerifiedFalse(email);

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
            verification.setExpiryTime(LocalDateTime.now().plusMinutes(15));
        }

        emailVerificationRepository.save(verification);

        // Send OTP via email asynchronously
        CompletableFuture.runAsync(() -> {
            String subject = "Papay Moni - Email Verification";
            String htmlBody = buildVerificationEmail(otp);
            emailService.sendHtmlMessage(email, subject, htmlBody);
        });

        return true;
    }

    @Override
    @Transactional
    public boolean verifyOtp(String email, String otp) {
        log.info("Verifying OTP for email: {}", email);

        // Check cache first
        Cache.ValueWrapper cachedResult = cacheManager.getCache(VERIFICATION_CACHE).get(email + ":" + otp);
        if (cachedResult != null) {
            return (Boolean) cachedResult.get();
        }

        Optional<EmailVerification> verificationOpt =
                emailVerificationRepository.findByEmailAndVerifiedFalse(email);

        if (!verificationOpt.isPresent()) {
            log.warn("No pending verification found for email: {}", email);
            cacheManager.getCache(VERIFICATION_CACHE).put(email + ":" + otp, false);
            return false;
        }

        EmailVerification verification = verificationOpt.get();

        // Check if OTP is expired
        if (verification.isExpired()) {
            log.warn("OTP has expired for email: {}", email);
            cacheManager.getCache(VERIFICATION_CACHE).put(email + ":" + otp, false);
            return false;
        }

        // Check if OTP matches
        if (!verification.getOtp().equals(otp)) {
            log.warn("Invalid OTP provided for email: {}", email);
            cacheManager.getCache(VERIFICATION_CACHE).put(email + ":" + otp, false);
            return false;
        }

        // Mark as verified
        verification.setVerified(true);
        verification.setVerifiedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        // Update cache
        cacheManager.getCache(VERIFICATION_CACHE).put(email, true);
        cacheManager.getCache(VERIFICATION_CACHE).put(email + ":" + otp, true);

        // Clear attempts
        cacheManager.getCache(ATTEMPTS_CACHE).evict(email);

        log.info("Email verified successfully: {}", email);
        return true;
    }

    @Override
    @Cacheable(value = VERIFICATION_CACHE, key = "#email")
    public boolean isEmailVerified(String email) {
        return emailVerificationRepository.existsByEmailAndVerifiedTrue(email);
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1000000));
    }

    private String buildVerificationEmail(String otp) {
        return "<div style='font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
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
    }
}