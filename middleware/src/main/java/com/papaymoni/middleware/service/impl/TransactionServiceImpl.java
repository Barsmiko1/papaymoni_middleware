package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.TransactionRepository;
import com.papaymoni.middleware.service.TransactionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    @Override
    public List<Transaction> getUserTransactions(User user) {
        return transactionRepository.findByUser(user);
    }

    @Override
    public List<Transaction> getUserTransactionsByType(User user, String type) {
        return transactionRepository.findByUserAndTransactionType(user, type);
    }

    @Override
    public List<Transaction> getUserTransactionsByDateRange(User user, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.findByUserAndCreatedAtBetween(user, start, end);
    }

    @Override
    public Transaction findMatchingDeposit(User user, BigDecimal amount, String reference) {
        // Find a deposit transaction that matches the amount and has a reference to the target user
        List<Transaction> transactions = transactionRepository.findByUserAndAmountAndStatus(user, amount, "COMPLETED");

        // Filter by reference (could be in payment details or external reference)
        return transactions.stream()
                .filter(t -> "DEPOSIT".equals(t.getTransactionType()) &&
                        (t.getPaymentDetails() != null && t.getPaymentDetails().contains(reference)) ||
                        (t.getExternalReference() != null && t.getExternalReference().contains(reference)))
                .findFirst()
                .orElse(null);
    }
}
