package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.BvnVerificationResultDto;

import java.time.LocalDate;

public interface BvnVerificationService {
    /**
     * Verify BVN details against the provided information
     * @param bvn the BVN to verify
     * @param firstName the first name to match
     * @param lastName the last name to match
     * @param dateOfBirth the date of birth to match
     * @param gender the gender to match
     * @return verification result with match details
     */
    BvnVerificationResultDto verifyBvn(String bvn, String firstName, String lastName, LocalDate dateOfBirth, String gender);

    /**
     * Check if BVN is already verified
     * @param bvn the BVN to check
     * @return true if BVN is verified
     */
    boolean isBvnVerified(String bvn);
}