package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Cacheable(value = "transactionById", key = "#id")
    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findByIdWithFetch(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    @Override
    @Cacheable(value = "transactionsByUser", key = "#user.id")
    @Transactional(readOnly = true)
    public List<Transaction> getUserTransactions(User user) {
        return transactionRepository.findByUserWithFetch(user.getId());
    }

    @Override
    @Cacheable(value = "transactionsByUserAndType", key = "#user.id + '-' + #type")
    @Transactional(readOnly = true)
    public List<Transaction> getUserTransactionsByType(User user, String type) {
        // First get all transactions with proper fetching to avoid N+1 queries
        List<Transaction> allTransactions = transactionRepository.findByUserWithFetch(user.getId());

        // Then filter by type in memory
        return allTransactions.stream()
                .filter(t -> type.equals(t.getTransactionType()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getUserTransactionsByDateRange(User user, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.findByUserAndCreatedAtBetweenWithFetch(user.getId(), start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Transaction findMatchingDeposit(User user, BigDecimal amount, String reference) {
        // Find a deposit transaction that matches the amount and has a reference to the target user
        List<Transaction> transactions = transactionRepository.findByUserAndAmountAndStatus(user, amount, "COMPLETED");

        // Filter by reference (could be in payment details or external reference)
        return transactions.stream()
                .filter(t -> "DEPOSIT".equals(t.getTransactionType()) &&
                        ((t.getPaymentDetails() != null && t.getPaymentDetails().contains(reference)) ||
                                (t.getExternalReference() != null && t.getExternalReference().contains(reference))))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Cacheable(value = "recentTransactions", key = "#user.id + '-' + #limit")
    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions(User user, int limit) {
        // Get all transactions and then limit them
        List<Transaction> allTransactions = transactionRepository.findByUserWithFetch(user.getId());

        // Sort by createdAt in descending order and limit
        return allTransactions.stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}