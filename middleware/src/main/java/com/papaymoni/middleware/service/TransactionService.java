package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {
    Transaction getTransactionById(Long id);
    List<Transaction> getUserTransactions(User user);
    List<Transaction> getUserTransactionsByType(User user, String type);
    List<Transaction> getUserTransactionsByDateRange(User user, LocalDateTime start, LocalDateTime end);
    Transaction findMatchingDeposit(User user, BigDecimal amount, String reference);
}
