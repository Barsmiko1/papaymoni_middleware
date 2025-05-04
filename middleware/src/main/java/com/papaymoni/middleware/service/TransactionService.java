package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {
    /**
     * Get a transaction by ID with optimized fetching
     * @param id Transaction ID
     * @return Transaction
     */
    Transaction getTransactionById(Long id);

    /**
     * Get all transactions for a user with optimized fetching
     * @param user User object
     * @return List of transactions
     */
    List<Transaction> getUserTransactions(User user);

    /**
     * Get user transactions by type
     * @param user User object
     * @param type Transaction type
     * @return List of transactions
     */
    List<Transaction> getUserTransactionsByType(User user, String type);

    /**
     * Get user transactions by date range with optimized fetching
     * @param user User object
     * @param start Start date
     * @param end End date
     * @return List of transactions
     */
    List<Transaction> getUserTransactionsByDateRange(User user, LocalDateTime start, LocalDateTime end);

    /**
     * Find matching deposit transaction
     * @param user User object
     * @param amount Amount
     * @param reference Reference
     * @return Transaction or null
     */
    Transaction findMatchingDeposit(User user, BigDecimal amount, String reference);

    /**
     * Get recent transactions for a user
     * @param user User object
     * @param limit Number of transactions to return
     * @return List of recent transactions
     */
    List<Transaction> getRecentTransactions(User user, int limit);
}