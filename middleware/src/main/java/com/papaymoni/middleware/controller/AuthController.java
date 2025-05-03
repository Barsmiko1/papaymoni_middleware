// Updated AuthController.java - removing the nested AccountController class
package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.*;
import com.papaymoni.middleware.service.EmailVerificationService;
import com.papaymoni.middleware.service.RegistrationService;
import com.papaymoni.middleware.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final CacheManager cacheManager;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> registerUser(
            @Valid @RequestBody UserRegistrationDto registrationDto) {

        try {
            ApiResponse<User> result = registrationService.registerUser(registrationDto);
            return new ResponseEntity<>(result, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Registration error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed due to an unexpected error"));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<User>> verifyEmail(
            @Valid @RequestBody EmailVerificationDtos.OtpVerificationRequestDto verificationDto) {

        boolean verified = emailVerificationService.verifyOtp(
                verificationDto.getEmail(),
                verificationDto.getOtp()
        );

        if (!verified) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid or expired OTP"));
        }

        User user = registrationService.completeRegistration(verificationDto.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "Email verified successfully. Registration complete.", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> authenticateUser(@Valid @RequestBody UserLoginDto loginDto) {
        log.info("Processing login for user: {}", loginDto.getUsernameOrEmail());

        try {
            // Check if email is verified (if login is with email)
            if (loginDto.getUsernameOrEmail().contains("@")) {
                boolean isEmailVerified = emailVerificationService.isEmailVerified(loginDto.getUsernameOrEmail());
                if (!isEmailVerified) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                            ApiResponse.error("Email verification is required before login. Please verify your email."));
                }
            } else {
                // If login is with username, fetch user and check if email is verified
                User user = userService.getUserByUsername(loginDto.getUsernameOrEmail());
                boolean isEmailVerified = emailVerificationService.isEmailVerified(user.getEmail());
                if (!isEmailVerified) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                            ApiResponse.error("Email verification is required before login. Please verify your email."));
                }
            }

            // Proceed with authentication
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDto.getUsernameOrEmail(),
                            loginDto.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = tokenProvider.generateToken(authentication);

            // Cache the token with appropriate TTL
            String userKey = "auth:" + ((UserDetails)authentication.getPrincipal()).getUsername();
            cacheManager.getCache("userAuth").put(userKey, jwt);

            return ResponseEntity.ok(ApiResponse.success("User logged in successfully", new JwtAuthResponse(jwt)));
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for user: {}", loginDto.getUsernameOrEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.error("Invalid username or password"));
        } catch (Exception e) {
            log.error("Error during login for user: {}", loginDto.getUsernameOrEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An error occurred during login: " + e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody EmailVerificationDtos.EmailVerificationRequestDto dto) {

        // Implement rate limiting using cache
        String rateLimitKey = "email:verification:" + dto.getEmail();
        Integer attempts = cacheManager.getCache("rateLimits").get(rateLimitKey, Integer.class);

        if (attempts != null && attempts >= 3) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many verification attempts. Please try again later.", null));
        }

        boolean sent = emailVerificationService.sendOtp(dto.getEmail());

        // Update rate limit counter with TTL
        cacheManager.getCache("rateLimits").put(rateLimitKey, attempts == null ? 1 : attempts + 1);
        // Set TTL for rate limit (1 hour)
        // Note: This requires a custom cache configuration with TTL support

        if (sent) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Verification code sent successfully", null));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send verification code"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            // Remove token from cache
            cacheManager.getCache("userAuth").evict("auth:" + username);
            // Invalidate token in token provider
            tokenProvider.invalidateToken(username);

            SecurityContextHolder.clearContext();
            return ResponseEntity.ok(ApiResponse.success("Successfully logged out", null));
        }
        return ResponseEntity.ok(ApiResponse.success("No active session to logout", null));
    }
}