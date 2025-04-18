package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.BybitCredentialsDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.BybitCredentialsRepository;
import com.papaymoni.middleware.service.BybitApiService;
import com.papaymoni.middleware.service.BybitCredentialsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BybitCredentialsServiceImpl implements BybitCredentialsService {

    private final BybitCredentialsRepository bybitCredentialsRepository;
    private final BybitApiService bybitApiService;

    public BybitCredentialsServiceImpl(BybitCredentialsRepository bybitCredentialsRepository,
                                       BybitApiService bybitApiService) {
        this.bybitCredentialsRepository = bybitCredentialsRepository;
        this.bybitApiService = bybitApiService;
    }

    @Override
    @Transactional
    public BybitCredentials saveCredentials(User user, BybitCredentialsDto credentialsDto) {
        // Check if user already has credentials
        BybitCredentials credentials = bybitCredentialsRepository.findByUser(user)
                .orElse(new BybitCredentials());

        credentials.setUser(user);
        credentials.setApiKey(credentialsDto.getApiKey());
        credentials.setApiSecret(credentialsDto.getApiSecret());

        // Verify credentials with Bybit API
        boolean isValid = bybitApiService.verifyCredentials(credentials);
        credentials.setVerified(isValid);

        if (isValid) {
            credentials.setLastVerified(LocalDateTime.now());
        }

        return bybitCredentialsRepository.save(credentials);
    }

    @Override
    public BybitCredentials getCredentialsByUser(User user) {
        return bybitCredentialsRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Bybit credentials not found for user: " + user.getUsername()));
    }

    @Override
    public boolean verifyCredentials(BybitCredentials credentials) {
        boolean isValid = bybitApiService.verifyCredentials(credentials);

        if (isValid) {
            credentials.setVerified(true);
            credentials.setLastVerified(LocalDateTime.now());
            bybitCredentialsRepository.save(credentials);
        }

        return isValid;
    }

    @Override
    @Transactional
    public void deleteCredentials(Long id) {
        bybitCredentialsRepository.deleteById(id);
    }

    @Override
    public boolean hasCredentials(User user) {
        return bybitCredentialsRepository.existsByUser(user);
    }
}
