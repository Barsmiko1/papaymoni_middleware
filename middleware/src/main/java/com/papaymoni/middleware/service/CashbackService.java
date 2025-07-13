package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;

public interface CashbackService {
    /**
     * Process cashback for a successful transaction
     * @param user the user who made the transaction
     * @param transactionAmount the amount of the transaction
     * @param currency the currency of the transaction
     * @param transactionType the type of transaction (WITHDRAWAL, EXCHANGE)
     */
    void processCashback(User user, BigDecimal transactionAmount, String currency, String transactionType);

    /**
     * Calculate cashback amount for a transaction
     * @param transactionAmount the transaction amount
     * @return cashback amount (0.01% of transaction)
     */
    BigDecimal calculateCashbackAmount(BigDecimal transactionAmount);
}
