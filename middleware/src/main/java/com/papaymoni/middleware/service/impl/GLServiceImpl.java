package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.model.GLEntry;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.GLEntryRepository;
import com.papaymoni.middleware.service.GLService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GLServiceImpl implements GLService {

    private final GLEntryRepository glEntryRepository;

    @Override
    @Transactional
    public GLEntry createEntry(User user, String entryType, String accountType, BigDecimal amount,
                               String currency, String description, Transaction transaction) {
        GLEntry entry = new GLEntry();
        entry.setUser(user);
        entry.setEntryType(entryType);
        entry.setAccountType(accountType);
        entry.setAmount(amount);
        entry.setCurrency(currency);
        entry.setDescription(description);
        entry.setTransaction(transaction);

        return glEntryRepository.save(entry);
    }

    @Override
    public BigDecimal getUserBalance(User user) {
        BigDecimal balance = glEntryRepository.getUserBalance(user);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getUserBalanceByCurrency(User user, String currency) {
        BigDecimal balance = glEntryRepository.getUserBalanceByCurrency(user, currency);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public void debitUserAccount(User user, BigDecimal amount) {
        createEntry(user, "DEBIT", "USER", amount, "NGN", "Debit from user account", null);
    }

    @Override
    @Transactional
    public void creditUserAccount(User user, BigDecimal amount) {
        createEntry(user, "CREDIT", "USER", amount, "NGN", "Credit to user account", null);
    }

    @Override
    @Transactional
    public void creditFeeAccount(BigDecimal amount) {
        createEntry(null, "CREDIT", "FEE", amount, "NGN", "Fee collected", null);
    }

    @Override
    public List<GLEntry> getUserEntries(User user) {
        return glEntryRepository.findByUser(user);
    }

    /**
     * Check if a user has sufficient balance
     * @param user the user to check
     * @param amount the amount to check
     * @return true if the user has sufficient balance
     */
    @Override
    public boolean hasSufficientBalance(User user, BigDecimal amount) {
        BigDecimal balance = getUserBalance(user);
        log.info("Checking if user {} has sufficient balance: {} >= {}", user.getId(), balance, amount);
        return balance.compareTo(amount) >= 0;
    }
}
