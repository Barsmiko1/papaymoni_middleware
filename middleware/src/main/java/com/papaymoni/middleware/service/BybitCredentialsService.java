package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.BybitCredentialsDto;
import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.User;

public interface BybitCredentialsService {
    BybitCredentials saveCredentials(User user, BybitCredentialsDto credentialsDto);
    BybitCredentials getCredentialsByUser(User user);
    boolean verifyCredentials(BybitCredentials credentials);
    void deleteCredentials(Long id);
    boolean hasCredentials(User user);
}
