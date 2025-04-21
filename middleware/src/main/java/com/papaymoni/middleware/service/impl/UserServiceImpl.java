//package com.papaymoni.middleware.service.impl;
//
//import com.papaymoni.middleware.dto.UserProfileDto;
//import com.papaymoni.middleware.dto.UserRegistrationDto;
//import com.papaymoni.middleware.exception.ResourceNotFoundException;
//import com.papaymoni.middleware.model.User;
//import com.papaymoni.middleware.repository.UserRepository;
//import com.papaymoni.middleware.service.UserService;
//import com.papaymoni.middleware.util.ReferralCodeGenerator;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.cache.annotation.Cacheable;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//@Service
//public class UserServiceImpl implements UserService {
//
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//
//    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
//        this.userRepository = userRepository;
//        this.passwordEncoder = passwordEncoder;
//    }
//
//    @Override
//    @Transactional
//    public User registerUser(UserRegistrationDto registrationDto) {
//        // Check if username or email already exists
//        if (userRepository.existsByUsername(registrationDto.getUsername())) {
//            throw new IllegalArgumentException("Username already exists");
//        }
//
//        if (userRepository.existsByEmail(registrationDto.getEmail())) {
//            throw new IllegalArgumentException("Email already exists");
//        }
//
//        // Create new user
//        User user = new User();
//        user.setUsername(registrationDto.getUsername());
//        user.setEmail(registrationDto.getEmail());
//        user.setPassword(passwordEncoder.encode(registrationDto.getPassword()));
//        user.setFirstName(registrationDto.getFirstName());
//        user.setLastName(registrationDto.getLastName());
//        user.setPhoneNumber(registrationDto.getPhoneNumber());
//        user.setEmailVerified(false);
//        user.setPhoneVerified(false);
//
//        // Generate referral code
//        user.setReferralCode(ReferralCodeGenerator.generateReferralCode());
//
//        // Set referrer if provided
//        if (registrationDto.getReferralCode() != null && !registrationDto.getReferralCode().isEmpty()) {
//            userRepository.findByReferralCode(registrationDto.getReferralCode())
//                    .ifPresent(referrer -> user.setReferredBy(referrer.getUsername()));
//        }
//
//        return userRepository.save(user);
//    }
//
//    @Override
//    @Cacheable(value = "users", key = "#id")
//    public User getUserById(Long id) {
//        return userRepository.findById(id)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
//    }
//
//    @Override
//    @Cacheable(value = "users", key = "#username")
//    public User getUserByUsername(String username) {
//        return userRepository.findByUsername(username)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
//    }
//
//    @Override
//    @Cacheable(value = "users", key = "#email")
//    public User getUserByEmail(String email) {
//        return userRepository.findByEmail(email)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
//    }
//
//    @Override
//    public List<User> getAllUsers() {
//        return userRepository.findAll();
//    }
//
//    @Override
//    @Transactional
//    public User updateUser(User user) {
//        return userRepository.save(user);
//    }
//
//    @Override
//    @Transactional
//    public void deleteUser(Long id) {
//        userRepository.deleteById(id);
//    }
//
//    @Override
//    public boolean existsByUsername(String username) {
//        return userRepository.existsByUsername(username);
//    }
//
//    @Override
//    public boolean existsByEmail(String email) {
//        return userRepository.existsByEmail(email);
//    }
//
//    @Override
//    @Cacheable(value = "users", key = "#usernameOrEmail")
//    public User getUserByUsernameOrEmail(String usernameOrEmail) {
//        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + usernameOrEmail));
//    }
//
//    private UserProfileDto convertToDto(User user) {
//        UserProfileDto dto = new UserProfileDto();
//        dto.setId(user.getId());
//        dto.setUsername(user.getUsername());
//        dto.setEmail(user.getEmail());
//        dto.setFirstName(user.getFirstName());
//        dto.setLastName(user.getLastName());
//        dto.setPhoneNumber(user.getPhoneNumber());
//        dto.setReferralCode(user.getReferralCode());
//        dto.setVirtualAccounts(user.getVirtualAccounts().stream().collect(Collectors.toList()));
//        return dto;
//    }
//}


package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.UserProfileDto;
import com.papaymoni.middleware.dto.UserRegistrationDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.UserService;
import com.papaymoni.middleware.util.ReferralCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User registerUser(UserRegistrationDto registrationDto) {
        // Check if username or email already exists
        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(registrationDto.getUsername());
        user.setEmail(registrationDto.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDto.getPassword()));
        user.setFirstName(registrationDto.getFirstName());
        user.setLastName(registrationDto.getLastName());
        user.setPhoneNumber(registrationDto.getPhoneNumber());
        user.setEmailVerified(false);
        user.setPhoneVerified(false);

        // Generate referral code
        user.setReferralCode(ReferralCodeGenerator.generateReferralCode());

        // Set referrer if provided
        if (registrationDto.getReferralCode() != null && !registrationDto.getReferralCode().isEmpty()) {
            userRepository.findByReferralCode(registrationDto.getReferralCode())
                    .ifPresent(referrer -> user.setReferredBy(referrer.getUsername()));
        }

        return userRepository.save(user);
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Cacheable(value = "users", key = "#username")
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Override
    @Cacheable(value = "users", key = "#email")
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Cacheable(value = "users", key = "#usernameOrEmail")
    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + usernameOrEmail));
    }

    // New method implementation for optimized user profile retrieval
    @Override
    @Cacheable(value = "userProfiles", key = "#username")
    public UserProfileDto getUserProfileByUsername(String username) {
        log.debug("Fetching user profile for username: {}", username);

        // Use the repository method that fetches user with virtual accounts
        User user = userRepository.findByUsernameWithVirtualAccount(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        return convertToDto(user);
    }

    // Moved from controller to service for better encapsulation
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