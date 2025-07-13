package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;

public interface ReferralService {
    /**
     * Process referral bonus when user reaches transaction milestone
     * @param user the user who was referred
     * @param transactionAmount the amount of the transaction
     * @param currency the currency of the transaction
     */
    void processReferralBonus(User user, BigDecimal transactionAmount, String currency);

    /**
     * Check if user is eligible for referral bonus
     * @param user the referred user
     * @return true if eligible for referral bonus
     */
    boolean isEligibleForReferralBonus(User user);

    /**
     * Get current referral bonus amount
     * @return bonus amount
     */
    BigDecimal getReferralBonusAmount();
}