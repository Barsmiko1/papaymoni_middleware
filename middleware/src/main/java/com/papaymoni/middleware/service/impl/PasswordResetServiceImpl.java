package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.PasswordResetDto;
import com.papaymoni.middleware.model.PasswordResetToken;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.PasswordResetTokenRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.PasswordResetService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    // Token expiry time in hours
    private static final int TOKEN_EXPIRY_HOURS = 1;

    @Override
    @Transactional
    public ApiResponse<Void> initiatePasswordReset(String email) {
        log.info("Initiating password reset for email: {}", email);

        // Check cache first for better performance
        User user = null;
        String cacheKey = "email:" + email;

        // Try to get from cache
        user = cacheManager.getCache("users").get(cacheKey, User.class);

        // If not in cache, check database
        if (user == null) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (!userOpt.isPresent()) {
                log.warn("Password reset requested for non-existent email: {}", email);
                return ApiResponse.error("No account found with this email address");
            }
            user = userOpt.get();
        }

        // Invalidate any existing tokens for this user
        invalidateExistingTokens(user);

        // Generate a new token
        String token = generateResetToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        tokenRepository.save(resetToken);

        // Send reset email
        boolean emailSent = notificationService.sendPasswordResetEmail(email, token);

        if (!emailSent) {
            log.error("Failed to send password reset email to: {}", email);
            return ApiResponse.error("Failed to send password reset email. Please try again later.");
        }

        log.info("Password reset email sent successfully to: {}", email);
        return new ApiResponse<>(true, "Password reset instructions have been sent to your email", null);
    }

    @Override
    public ApiResponse<Boolean> validateResetToken(String token) {
        log.info("Validating password reset token");

        Optional<PasswordResetToken> tokenOpt = tokenRepository.findValidToken(token, LocalDateTime.now());

        if (!tokenOpt.isPresent()) {
            log.warn("Invalid or expired password reset token");
            return ApiResponse.error("Invalid or expired password reset token", false);
        }

        return ApiResponse.success("Token is valid", true);
    }

    @Override
    @Transactional
    public ApiResponse<Void> resetPassword(PasswordResetDto.ResetDto resetDto) {
        log.info("Processing password reset request");

        // Validate token
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findValidToken(resetDto.getToken(), LocalDateTime.now());

        if (!tokenOpt.isPresent()) {
            log.warn("Invalid or expired password reset token");
            return ApiResponse.error("Invalid or expired password reset token");
        }

        PasswordResetToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();

        // Validate password confirmation
        if (!resetDto.getPassword().equals(resetDto.getConfirmPassword())) {
            return ApiResponse.error("Passwords do not match");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(resetDto.getPassword()));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Clear user from cache to ensure updated password is used
        cacheManager.getCache("users").evict("email:" + user.getEmail());
        cacheManager.getCache("users").evict(user.getUsername());
        cacheManager.getCache("userAuth").evict("auth:" + user.getUsername());

        log.info("Password reset successful for user: {}", user.getUsername());
        return new ApiResponse<>(true, "Password has been reset successfully", null);
    }

    private void invalidateExistingTokens(User user) {
        List<PasswordResetToken> existingTokens = tokenRepository.findByUser(user);
        for (PasswordResetToken token : existingTokens) {
            token.setUsed(true);
            tokenRepository.save(token);
        }
    }

    private String generateResetToken() {
        return UUID.randomUUID().toString();
    }
}
