package com.papaymoni.middleware.controller;
import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.security.*;
import com.papaymoni.middleware.service.EmailVerificationService;
import com.papaymoni.middleware.service.RegistrationService;
import com.papaymoni.middleware.service.UserService;
import com.papaymoni.middleware.service.VirtualAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        private void cacheUserAuth(User user, String jwt) {
        // Cache the JWT with the user's info for faster token validation
        // This should be stored with the same TTL as the JWT
        cacheManager.getCache("userAuth").put(user.getId(), jwt);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody EmailVerificationDtos.EmailVerificationRequestDto dto) {

        // Implement rate limiting here to prevent abuse
        boolean sent = emailVerificationService.sendOtp(dto.getEmail());

        if (sent) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Verification code sent successfully", null));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send verification code"));
        }
    }

    @RequestMapping("/api/accounts")
    public class AccountController {

        private final VirtualAccountService virtualAccountService;
        private final UserRepository userRepository;
        private final CacheManager cacheManager;

        // Simple in-memory rate limiting
        private final Map<String, Long> lastRequestTimestamps = new HashMap<>();
        private static final long RATE_LIMIT_WINDOW_MS = 3600000; // 1 hour
        private static final int MAX_REQUESTS_PER_WINDOW = 5;

        public AccountController(
                VirtualAccountService virtualAccountService,
                UserRepository userRepository,
                CacheManager cacheManager) {
            this.virtualAccountService = virtualAccountService;
            this.userRepository = userRepository;
            this.cacheManager = cacheManager;
        }

        /**
         * Get all virtual accounts for the authenticated user
         */
        @GetMapping("/virtual")
        public ResponseEntity<ApiResponse> getVirtualAccounts(@AuthenticationPrincipal UserDetails currentUser) {
            try {
                log.debug("Fetching virtual accounts for user: {}", currentUser.getUsername());

                // Check cache first
                User user = cacheManager.getCache("users").get(currentUser.getUsername(), User.class);
                if (user == null) {
                    user = userRepository.findByUsername(currentUser.getUsername())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                    // Cache user for future requests
                    cacheManager.getCache("users").put(currentUser.getUsername(), user);
                }

                List<VirtualAccount> accounts = virtualAccountService.getUserVirtualAccounts(user);

                return ResponseEntity.ok(ApiResponse.success("Virtual accounts retrieved successfully", accounts));
            } catch (ResourceNotFoundException e) {
                log.error("User not found: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage(), "USER_NOT_FOUND"));
            } catch (Exception e) {
                log.error("Error retrieving virtual accounts: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Failed to retrieve virtual accounts: " + e.getMessage(),
                                "VIRTUAL_ACCOUNTS_ERROR"));
            }
        }

        /**
         * Create a new virtual account for the authenticated user
         */
        @PostMapping("/virtual")
        public ResponseEntity<ApiResponse> createVirtualAccount(
                @AuthenticationPrincipal UserDetails currentUser,
                @Valid @RequestBody VirtualAccountDto accountDto) {

            // Apply rate limiting
            if (isRateLimited(currentUser.getUsername())) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("Too many account creation requests. Please try again later.",
                                "RATE_LIMIT_EXCEEDED"));
            }

            try {
                log.debug("Creating virtual account for user: {} with currency: {}",
                        currentUser.getUsername(), accountDto.getCurrency());

                // Check cache first
                User user = cacheManager.getCache("users").get(currentUser.getUsername(), User.class);
                if (user == null) {
                    user = userRepository.findByUsername(currentUser.getUsername())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                    // Cache user for future requests
                    cacheManager.getCache("users").put(currentUser.getUsername(), user);
                }

                // Verify BVN status
                if (!user.isBvnVerified()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("BVN verification is required to create a virtual account",
                                    "BVN_NOT_VERIFIED"));
                }

                VirtualAccount account = virtualAccountService.createVirtualAccount(user, accountDto.getCurrency());

                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success("Virtual account created successfully", account));
            } catch (ResourceNotFoundException e) {
                log.error("User not found: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage(), "USER_NOT_FOUND"));
            } catch (IllegalStateException e) {
                log.error("Cannot create virtual account: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(e.getMessage(), "BVN_NOT_VERIFIED"));
            } catch (Exception e) {
                log.error("Error creating virtual account: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Failed to create virtual account: " + e.getMessage(),
                                "CREATE_ACCOUNT_ERROR"));
            }
        }

        /**
         * Get virtual accounts filtered by currency
         */
        @GetMapping("/virtual/currency/{currency}")
        public ResponseEntity<ApiResponse> getVirtualAccountsByCurrency(
                @AuthenticationPrincipal UserDetails currentUser,
                @PathVariable String currency) {

            try {
                log.debug("Fetching virtual accounts for user: {} with currency: {}",
                        currentUser.getUsername(), currency);

                // Check cache first
                User user = cacheManager.getCache("users").get(currentUser.getUsername(), User.class);
                if (user == null) {
                    user = userRepository.findByUsername(currentUser.getUsername())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                    // Cache user for future requests
                    cacheManager.getCache("users").put(currentUser.getUsername(), user);
                }

                List<VirtualAccount> accounts = virtualAccountService.getUserVirtualAccountsByCurrency(user, currency);

                return ResponseEntity.ok(ApiResponse.success(
                        "Virtual accounts with currency " + currency + " retrieved successfully", accounts));
            } catch (ResourceNotFoundException e) {
                log.error("User not found: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage(), "USER_NOT_FOUND"));
            } catch (Exception e) {
                log.error("Error retrieving virtual accounts by currency: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Failed to retrieve virtual accounts: " + e.getMessage(),
                                "VIRTUAL_ACCOUNTS_ERROR"));
            }
        }

        /**
         * Get a specific virtual account by ID
         */
        @GetMapping("/virtual/{id}")
        public ResponseEntity<ApiResponse> getVirtualAccountById(
                @AuthenticationPrincipal UserDetails currentUser,
                @PathVariable Long id) {

            try {
                log.debug("Fetching virtual account with ID: {} for user: {}", id, currentUser.getUsername());

                // Check cache first for user
                User user = cacheManager.getCache("users").get(currentUser.getUsername(), User.class);
                if (user == null) {
                    user = userRepository.findByUsername(currentUser.getUsername())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                    // Cache user for future requests
                    cacheManager.getCache("users").put(currentUser.getUsername(), user);
                }

                VirtualAccount account = virtualAccountService.getVirtualAccountById(id);

                // Security check: make sure the account belongs to the authenticated user
                if (!account.getUser().getUsername().equals(currentUser.getUsername())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.error("You do not have permission to access this account", "ACCESS_DENIED"));
                }

                return ResponseEntity.ok(ApiResponse.success("Virtual account retrieved successfully", account));
            } catch (ResourceNotFoundException e) {
                log.error("Resource not found: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage(), "RESOURCE_NOT_FOUND"));
            } catch (Exception e) {
                log.error("Error retrieving virtual account: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Failed to retrieve virtual account: " + e.getMessage(),
                                "GET_ACCOUNT_ERROR"));
            }
        }

        /**
         * Check if a user has exceeded rate limits
         */
        private boolean isRateLimited(String username) {
            String cacheKey = "rate_limit:" + username;

            // Try to get from cache first for better performance
            Integer count = cacheManager.getCache("rateLimits").get(cacheKey, Integer.class);
            if (count != null && count >= MAX_REQUESTS_PER_WINDOW) {
                log.warn("Rate limit exceeded for user: {}", username);
                return true;
            }

            // Fall back to in-memory tracking if cache doesn't have the data
            synchronized (lastRequestTimestamps) {
                long currentTime = System.currentTimeMillis();
                Long lastRequestTime = lastRequestTimestamps.get(username);

                if (lastRequestTime == null || (currentTime - lastRequestTime) > RATE_LIMIT_WINDOW_MS) {
                    // First request in window or window expired
                    lastRequestTimestamps.put(username, currentTime);

                    // Update cache
                    cacheManager.getCache("rateLimits").put(cacheKey, 1);
                    return false;
                } else {
                    // Increment the count in cache
                    if (count == null) {
                        count = 1;
                    }
                    count += 1;
                    cacheManager.getCache("rateLimits").put(cacheKey, count);

                    return count > MAX_REQUESTS_PER_WINDOW;
                }
            }
        }

        /**
         * Health check endpoint for virtual account service
         */
        @GetMapping("/virtual/health")
        public ResponseEntity<ApiResponse> checkVirtualAccountHealth() {
            Map<String, Object> healthData = new HashMap<>();

            try {
                // Check database connectivity
                long accountCount = virtualAccountService.getAccountCount();
                healthData.put("totalAccounts", accountCount);
                healthData.put("status", "UP");

                return ResponseEntity.ok(ApiResponse.success("Virtual account service is healthy", healthData));
            } catch (Exception e) {
                log.error("Health check failed", e);
                healthData.put("status", "DOWN");
                healthData.put("error", e.getMessage());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("Virtual account service is experiencing issues", healthData));
            }
        }
    }
}