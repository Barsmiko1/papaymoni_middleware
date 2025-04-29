package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.JwtTokenProvider;
import com.papaymoni.middleware.service.BvnVerificationService;
import com.papaymoni.middleware.service.EmailVerificationService;
import com.papaymoni.middleware.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final BvnVerificationService bvnVerificationService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtTokenProvider tokenProvider,
                          BvnVerificationService bvnVerificationService,
                          EmailVerificationService emailVerificationService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.bvnVerificationService = bvnVerificationService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/pre-register")
    public ResponseEntity<ApiResponse<?>> preRegisterUser(@Valid @RequestBody UserRegistrationDto registrationDto) {
        log.info("Processing pre-registration for user: {}", registrationDto.getUsername());

        // Check if username is already taken
        if (userService.existsByUsername(registrationDto.getUsername())) {
            return new ResponseEntity<>(ApiResponse.error("Username is already taken!"), HttpStatus.BAD_REQUEST);
        }

        // Check if email is already taken
        if (userService.existsByEmail(registrationDto.getEmail())) {
            return new ResponseEntity<>(ApiResponse.error("Email is already in use!"), HttpStatus.BAD_REQUEST);
        }

        // Verify BVN
        BvnVerificationResultDto bvnResult = bvnVerificationService.verifyBvn(
                registrationDto.getBvn(),
                registrationDto.getFirstName(),
                registrationDto.getLastName(),
                registrationDto.getDateOfBirth(),
                registrationDto.getGender()
        );

        // Store pre-registration data in response for client to use in next step
        Map<String, Object> preRegData = new HashMap<>();
        preRegData.put("username", registrationDto.getUsername());
        preRegData.put("email", registrationDto.getEmail());
        preRegData.put("bvnVerified", bvnResult.isVerified());
        preRegData.put("bvnVerificationDetails", bvnResult);

        // Send email verification OTP
        boolean otpSent = emailVerificationService.sendOtp(registrationDto.getEmail());
        preRegData.put("otpSent", otpSent);

        if (!bvnResult.isVerified()) {
            log.warn("BVN verification failed for user: {} - {}",
                    registrationDto.getUsername(), bvnResult.getMessage());
        }

        if (!otpSent) {
            log.error("Failed to send verification OTP to: {}", registrationDto.getEmail());
        }

        // Return pre-registration data
        return ResponseEntity.ok(ApiResponse.success(
                "Pre-registration processed successfully. Please verify your email.",
                preRegData));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> registerUser(@Valid @RequestBody UserRegistrationDto registrationDto) {
        log.info("Processing registration for user: {}", registrationDto.getUsername());

        // Check if username is already taken
        if (userService.existsByUsername(registrationDto.getUsername())) {
            return new ResponseEntity<>(ApiResponse.error("Username is already taken!"), HttpStatus.BAD_REQUEST);
        }

        // Check if email is already taken
        if (userService.existsByEmail(registrationDto.getEmail())) {
            return new ResponseEntity<>(ApiResponse.error("Email is already in use!"), HttpStatus.BAD_REQUEST);
        }

        // Verify that email has been verified
        boolean isEmailVerified = emailVerificationService.isEmailVerified(registrationDto.getEmail());
        if (!isEmailVerified) {
            return new ResponseEntity<>(ApiResponse.error(
                    "Email verification is required before registration. Please verify your email."),
                    HttpStatus.BAD_REQUEST);
        }

        // Create user (with conventional DTO for backward compatibility)
        UserRegistrationDto userRegistrationDto = new UserRegistrationDto();
        userRegistrationDto.setUsername(registrationDto.getUsername());
        userRegistrationDto.setEmail(registrationDto.getEmail());
        userRegistrationDto.setPassword(registrationDto.getPassword());
        userRegistrationDto.setFirstName(registrationDto.getFirstName());
        userRegistrationDto.setLastName(registrationDto.getLastName());
        userRegistrationDto.setPhoneNumber(registrationDto.getPhoneNumber());
        userRegistrationDto.setReferralCode(registrationDto.getReferralCode());

        User user = userService.registerUser(userRegistrationDto);

        // Verify BVN if not already verified
        if (!bvnVerificationService.isBvnVerified(registrationDto.getBvn())) {
            BvnVerificationResultDto bvnResult = bvnVerificationService.verifyBvn(
                    registrationDto.getBvn(),
                    registrationDto.getFirstName(),
                    registrationDto.getLastName(),
                    registrationDto.getDateOfBirth(),
                    registrationDto.getGender()
            );

            if (!bvnResult.isVerified()) {
                log.warn("BVN verification failed during registration for user: {} - {}",
                        user.getUsername(), bvnResult.getMessage());
            }
        }

        return new ResponseEntity<>(ApiResponse.success("User registered successfully", user), HttpStatus.CREATED);
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
}
