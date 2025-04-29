package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.BvnVerificationResultDto;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Component to update user BVN verification status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BvnVerificationStatusUpdater {

    private final UserRepository userRepository;

    /**
     * Update the BVN verification status for a user with the given BVN
     * @param bvn the BVN to update
     * @param verificationResult the verification result
     * @return true if user was found and updated
     */
    @Transactional
    public boolean updateBvnVerificationStatus(String bvn, BvnVerificationResultDto verificationResult) {
        log.info("Updating BVN verification status for BVN: {}", bvn);

        Optional<User> userOpt = userRepository.findByBvn(bvn);

        if (!userOpt.isPresent()) {
            log.warn("No user found with BVN: {}", bvn);
            return false;
        }

        User user = userOpt.get();
        user.setBvnVerified(verificationResult.isVerified());
        userRepository.save(user);

        log.info("Updated BVN verification status for user: {} to: {}",
                user.getUsername(), verificationResult.isVerified());

        return true;
    }
}