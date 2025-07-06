package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.GLEntry;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;
import java.util.List;

public interface GLService {
    GLEntry createEntry(User user, String entryType, String accountType, BigDecimal amount, String currency, String description, Transaction transaction);
    BigDecimal getUserBalance(User user);
    BigDecimal getUserBalanceByCurrency(User user, String currency);
    boolean hasSufficientBalance(User user, BigDecimal amount);
    boolean hasSufficientBalance(User user, BigDecimal amount, String currency);  // Add this new method signature
    void debitUserAccount(User user, BigDecimal amount);
    void creditUserAccount(User user, BigDecimal amount);
    void debitFeeAccount(BigDecimal amount);  // Added this method for fee refunds
    void creditFeeAccount(BigDecimal amount);
    List<GLEntry> getUserEntries(User user);
}