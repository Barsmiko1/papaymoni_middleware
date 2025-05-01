package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.BybitCredentialsDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.BybitCredentialsRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.BybitApiService;
import com.papaymoni.middleware.service.BybitCredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class BybitCredentialsServiceImpl implements BybitCredentialsService {

    private final BybitCredentialsRepository bybitCredentialsRepository;
    private final BybitApiService bybitApiService;
    private final UserRepository userRepository;

    public BybitCredentialsServiceImpl(BybitCredentialsRepository bybitCredentialsRepository,
                                       BybitApiService bybitApiService,
                                       UserRepository userRepository) {
        this.bybitCredentialsRepository = bybitCredentialsRepository;
        this.bybitApiService = bybitApiService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public BybitCredentials saveCredentials(User user, BybitCredentialsDto credentialsDto) {
        log.debug("Saving Bybit credentials for user: {}", user);

        // Safely get user ID
        Long userId = getUserId(user);

        // Fetch the actual user entity from the repository
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Check if user already has credentials
        BybitCredentials credentials = bybitCredentialsRepository.findByUser(userEntity)
                .orElse(new BybitCredentials());

        credentials.setUser(userEntity);
        credentials.setApiKey(credentialsDto.getApiKey());
        credentials.setApiSecret(credentialsDto.getApiSecret());

        // Save credentials first to ensure they have an ID
        credentials = bybitCredentialsRepository.save(credentials);

        // Now verify the credentials after they've been saved and have an ID
        try {
            boolean isValid = bybitApiService.verifyCredentials(credentials);
            credentials.setVerified(isValid);

            if (isValid) {
                credentials.setLastVerified(LocalDateTime.now());
            }

            log.debug("Credentials verified: {}", isValid);
        } catch (Exception e) {
            log.error("Error verifying credentials: ", e);
            credentials.setVerified(false);
        }

        // Save again with the verification status
        return bybitCredentialsRepository.save(credentials);
    }

    @Override
    public BybitCredentials getCredentialsByUser(User user) {
        log.debug("Getting Bybit credentials for user: {}", user);

        // Safely get user ID
        Long userId = getUserId(user);

        // Fetch the actual user entity from the repository
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return bybitCredentialsRepository.findByUser(userEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Bybit credentials not found for user: " + userEntity.getUsername()));
    }

    @Override
    public boolean verifyCredentials(BybitCredentials credentials) {
        log.debug("Verifying Bybit credentials: {}", credentials.getId());

        if (credentials == null) {
            log.warn("Cannot verify null credentials");
            return false;
        }

        try {
            boolean isValid = bybitApiService.verifyCredentials(credentials);

            if (isValid) {
                credentials.setVerified(true);
                credentials.setLastVerified(LocalDateTime.now());
                bybitCredentialsRepository.save(credentials);
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying credentials: ", e);
            return false;
        }
    }

    @Override
    @Transactional
    public void deleteCredentials(Long id) {
        log.debug("Deleting Bybit credentials with id: {}", id);
        bybitCredentialsRepository.deleteById(id);
    }

    @Override
    public boolean hasCredentials(User user) {
        log.debug("Checking if user has Bybit credentials: {}", user);

        // Safely get user ID
        Long userId = getUserId(user);

        // Fetch the actual user entity from the repository
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return bybitCredentialsRepository.existsByUser(userEntity);
    }

    /**
     * Helper method to safely extract user ID from different user representations
     */
    private Long getUserId(Object user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        // If it's already a proper User entity
        if (user instanceof User) {
            return ((User) user).getId();
        }

        // If it's a numeric ID directly
        if (user instanceof Number) {
            return ((Number) user).longValue();
        }

        // If it's a Map (like from JSON deserialization)
        if (user instanceof java.util.Map) {
            Object idObj = ((java.util.Map<?, ?>) user).get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue();
            } else if (idObj instanceof String) {
                try {
                    return Long.parseLong((String) idObj);
                } catch (NumberFormatException e) {
                    log.error("Failed to parse user ID from string: {}", idObj);
                }
            }
        }

        // If it's a String representation of a user ID
        if (user instanceof String) {
            try {
                return Long.parseLong((String) user);
            } catch (NumberFormatException e) {
                log.error("Failed to parse user ID from string: {}", user);
            }
        }

        // If we couldn't extract a valid ID
        log.error("Could not extract user ID from: {} (type: {})", user, user.getClass().getName());
        throw new IllegalArgumentException("Invalid user reference: Unable to extract user ID");
    }
}