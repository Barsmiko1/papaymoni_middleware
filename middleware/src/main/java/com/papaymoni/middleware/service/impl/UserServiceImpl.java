package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.dto.UserProfileDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.EmailVerificationService;
import com.papaymoni.middleware.service.UserService;
import com.papaymoni.middleware.util.ReferralCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    @Override
    @Transactional
    public User registerUser(UserRegistrationDto registrationDto) {
        log.info("Registering new user with username: {}", registrationDto.getUsername());

        // Check if username or email already exists
        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            log.warn("Username already exists: {}", registrationDto.getUsername());
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            log.warn("Email already exists: {}", registrationDto.getEmail());
            throw new IllegalArgumentException("Email already exists");
        }

        // Verify that email has been verified
        boolean isEmailVerified = emailVerificationService.isEmailVerified(registrationDto.getEmail());
        if (!isEmailVerified) {
            log.warn("Email not verified for registration: {}", registrationDto.getEmail());
            throw new IllegalArgumentException("Email must be verified before registration");
        }

        // Create new user
        User user = new User();
        user.setUsername(registrationDto.getUsername());
        user.setEmail(registrationDto.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDto.getPassword()));
        user.setFirstName(registrationDto.getFirstName());
        user.setLastName(registrationDto.getLastName());
        user.setPhoneNumber(registrationDto.getPhoneNumber());
        user.setEmailVerified(true); // Since we've verified the email already
        user.setPhoneVerified(false);

        // Generate referral code
        user.setReferralCode(ReferralCodeGenerator.generateReferralCode());

        // Set referrer if provided
        if (registrationDto.getReferralCode() != null && !registrationDto.getReferralCode().isEmpty()) {
            userRepository.findByReferralCode(registrationDto.getReferralCode())
                    .ifPresent(referrer -> user.setReferredBy(referrer.getUsername()));
        }

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        return savedUser;
    }

    @Override
    @Transactional
    public User registerEnhancedUser(UserRegistrationDto registrationDto) {
        log.info("Registering enhanced user with username: {}", registrationDto.getUsername());

        // Check if username or email already exists
        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            log.warn("Username already exists: {}", registrationDto.getUsername());
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            log.warn("Email already exists: {}", registrationDto.getEmail());
            throw new IllegalArgumentException("Email already exists");
        }

        // Check if BVN already exists
        if (registrationDto.getBvn() != null && !registrationDto.getBvn().isEmpty() &&
                userRepository.existsByBvn(registrationDto.getBvn())) {
            log.warn("BVN already exists: {}", registrationDto.getBvn().substring(0, 4) + "****");
            throw new IllegalArgumentException("BVN already exists in the system");
        }

        // Verify that email has been verified
        boolean isEmailVerified = emailVerificationService.isEmailVerified(registrationDto.getEmail());
        if (!isEmailVerified) {
            log.warn("Email not verified for registration: {}", registrationDto.getEmail());
            throw new IllegalArgumentException("Email must be verified before registration");
        }

        // Create new user with enhanced information
        User user = new User();
        user.setUsername(registrationDto.getUsername());
        user.setEmail(registrationDto.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDto.getPassword()));
        user.setFirstName(registrationDto.getFirstName());
        user.setLastName(registrationDto.getLastName());
        user.setPhoneNumber(registrationDto.getPhoneNumber());
        user.setEmailVerified(true); // Since we've verified the email already
        user.setPhoneVerified(false);

        // Enhanced fields
        user.setBvn(registrationDto.getBvn());
        user.setDateOfBirth(registrationDto.getDateOfBirth());
        user.setGender(registrationDto.getGender());

        // BVN verification status will be updated separately through the BvnVerificationService
        user.setBvnVerified(false);

        // Generate referral code
        user.setReferralCode(ReferralCodeGenerator.generateReferralCode());

        // Set referrer if provided
        if (registrationDto.getReferralCode() != null && !registrationDto.getReferralCode().isEmpty()) {
            userRepository.findByReferralCode(registrationDto.getReferralCode())
                    .ifPresent(referrer -> user.setReferredBy(referrer.getUsername()));
        }

        User savedUser = userRepository.save(user);
        log.info("Enhanced user registered successfully: {}", savedUser.getUsername());

        return savedUser;
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        log.debug("Getting user by ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User not found with id: " + id);
                });
    }

    @Override
    @Cacheable(value = "users", key = "#username")
    public User getUserByUsername(String username) {
        log.debug("Getting user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found with username: {}", username);
                    return new ResourceNotFoundException("User not found with username: " + username);
                });
    }

    @Override
    @Cacheable(value = "users", key = "#email")
    public User getUserByEmail(String email) {
        log.debug("Getting user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new ResourceNotFoundException("User not found with email: " + email);
                });
    }

    @Override
    public List<User> getAllUsers() {
        log.debug("Getting all users");
        return userRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, allEntries = true)
    public User updateUser(User user) {
        log.info("Updating user: {}", user.getUsername());

        // Verify user exists before updating
        if (!userRepository.existsById(user.getId())) {
            log.warn("User not found for update with ID: {}", user.getId());
            throw new ResourceNotFoundException("User not found with id: " + user.getId());
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, allEntries = true)
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        // Verify user exists before deleting
        if (!userRepository.existsById(id)) {
            log.warn("User not found for deletion with ID: {}", id);
            throw new ResourceNotFoundException("User not found with id: " + id);
        }

        userRepository.deleteById(id);
        log.info("User deleted successfully with ID: {}", id);
    }

    @Override
    public boolean existsByUsername(String username) {
        log.debug("Checking if username exists: {}", username);
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        log.debug("Checking if email exists: {}", email);
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByBvn(String bvn) {
        log.debug("Checking if BVN exists: {}", bvn.substring(0, 4) + "****");
        return userRepository.existsByBvn(bvn);
    }

    @Override
    @Cacheable(value = "users", key = "#usernameOrEmail")
    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        log.debug("Getting user by username or email: {}", usernameOrEmail);
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> {
                    log.warn("User not found with username or email: {}", usernameOrEmail);
                    return new ResourceNotFoundException("User not found with username or email: " + usernameOrEmail);
                });
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, key = "#email")
    public User updateEmailVerificationStatus(String email, boolean status) {
        log.info("Updating email verification status for: {} to: {}", email, status);

        User user = getUserByEmail(email);
        user.setEmailVerified(status);

        return userRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "userProfiles"}, allEntries = true)
    public User updateBvnVerificationStatus(String bvn, boolean status) {
        log.info("Updating BVN verification status for BVN: {} to: {}", bvn.substring(0, 4) + "****", status);

        User user = userRepository.findByBvn(bvn)
                .orElseThrow(() -> {
                    log.warn("User not found with BVN: {}", bvn.substring(0, 4) + "****");
                    return new ResourceNotFoundException("User not found with BVN: " + bvn.substring(0, 4) + "****");
                });

        user.setBvnVerified(status);
        return userRepository.save(user);
    }

    // New method implementation for optimized user profile retrieval
    @Override
    @Cacheable(value = "userProfiles", key = "#username")
    public UserProfileDto getUserProfileByUsername(String username) {
        log.debug("Fetching user profile for username: {}", username);

        // Use the repository method that fetches user with virtual accounts
        User user = userRepository.findByUsernameWithVirtualAccount(username)
                .orElseThrow(() -> {
                    log.warn("User not found with username: {}", username);
                    return new ResourceNotFoundException("User not found with username: " + username);
                });

        return convertToDto(user);
    }

    // Helper method to convert User to UserProfileDto
    private UserProfileDto convertToDto(User user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setReferralCode(user.getReferralCode());

        // Select the first virtual account if available to set on the DTO
        if (user.getVirtualAccounts() != null && !user.getVirtualAccounts().isEmpty()) {
            VirtualAccount firstAccount = user.getVirtualAccounts().iterator().next();
            dto.setVirtualAccount(firstAccount);
            log.debug("Found virtual account with ID: {} for user: {}", firstAccount.getId(), user.getUsername());
        } else {
            log.debug("No virtual accounts found for user: {}", user.getUsername());
        }

        return dto;
    }
}