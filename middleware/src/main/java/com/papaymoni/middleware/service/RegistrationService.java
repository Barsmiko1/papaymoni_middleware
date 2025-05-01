package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.BvnVerificationResultDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.util.ReferralCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final BvnVerificationService bvnVerificationService;
    private final EncryptionService encryptionService;
    private final CacheManager cacheManager;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public ApiResponse<User> registerUser(UserRegistrationDto registrationDto) {
        log.info("Processing registration for user: {}", registrationDto.getUsername());

        // Step 1: Validate user inputs
        validateRegistrationInputs(registrationDto);

        // Step 2: Create user entity with all provided data
        User user = createUserEntity(registrationDto);

        // Step 3: Verify BVN if provided
        BvnVerificationResultDto bvnResult = null;
        if (registrationDto.getBvn() != null && !registrationDto.getBvn().isEmpty()) {
            bvnResult = bvnVerificationService.verifyBvn(
                    registrationDto.getBvn(),
                    registrationDto.getFirstName(),
                    registrationDto.getLastName(),
                    registrationDto.getDateOfBirth(),
                    registrationDto.getGender()
            );

            // Encrypt BVN before saving
            user.setBvn(encryptionService.encrypt(registrationDto.getBvn()));
            user.setBvnVerified(bvnResult.isVerified());
        }

        // Step 4: Save user to generate ID (needed for cache keys)
        User savedUser = userRepository.save(user);

        // Step 5: Send email verification OTP
        boolean otpSent = emailVerificationService.sendOtp(registrationDto.getEmail());

        // Step 6: Cache user data with TTL
        cacheUserData(savedUser);

        // Step 7: Publish user created event
        publishUserCreatedEvent(savedUser);

        // Step 8: Return response
        String message = "Registration successful. Please verify your email.";
        return ApiResponse.success(message, savedUser);
    }

    private void validateRegistrationInputs(UserRegistrationDto dto) {
        // Check username uniqueness
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Check email uniqueness
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Check BVN uniqueness if provided
        if (dto.getBvn() != null && !dto.getBvn().isEmpty() &&
                userRepository.existsByBvn(encryptionService.encrypt(dto.getBvn()))) {
            throw new IllegalArgumentException("BVN is already registered");
        }
    }

    private User createUserEntity(UserRegistrationDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setEmailVerified(false);
        user.setPhoneVerified(false);

        // Set additional fields if provided
        if (dto.getDateOfBirth() != null) {
            user.setDateOfBirth(dto.getDateOfBirth());
        }

        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }

        // Generate referral code
        user.setReferralCode(ReferralCodeGenerator.generateReferralCode());

        // Set referrer if provided
        if (dto.getReferralCode() != null && !dto.getReferralCode().isEmpty()) {
            userRepository.findByReferralCode(dto.getReferralCode())
                    .ifPresent(referrer -> user.setReferredBy(referrer.getUsername()));
        }

        return user;
    }

    private void cacheUserData(User user) {
        // Cache user by ID
        cacheManager.getCache("users").put(user.getId(), user);

        // Cache user by username
        cacheManager.getCache("users").put(user.getUsername(), user);

        // Cache user by email
        cacheManager.getCache("users").put(user.getEmail(), user);
    }

    private void publishUserCreatedEvent(User user) {
        // Publish event for other services
        Map<String, Object> event = new HashMap<>();
        event.put("userId", user.getId());
        event.put("username", user.getUsername());
        event.put("email", user.getEmail());
        event.put("timestamp", System.currentTimeMillis());

        rabbitTemplate.convertAndSend("user-exchange", "user.created", event);
    }

    // Method to complete registration after email verification
    @Transactional
    public User completeRegistration(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        user.setEmailVerified(true);
        User updatedUser = userRepository.save(user);

        // Update cache
        cacheUserData(updatedUser);

        // Publish registration completed event
        publishRegistrationCompletedEvent(updatedUser);

        return updatedUser;
    }

    private void publishRegistrationCompletedEvent(User user) {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", user.getId());
        event.put("username", user.getUsername());
        event.put("timestamp", System.currentTimeMillis());

        rabbitTemplate.convertAndSend("user-exchange", "user.registration.completed", event);
    }
}
