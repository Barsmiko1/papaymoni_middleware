package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUser(User user);
    List<Transaction> findByUserAndTransactionType(User user, String transactionType);
    List<Transaction> findByUserAndStatus(User user, String status);
    List<Transaction> findByUserAndCreatedAtBetween(User user, LocalDateTime start, LocalDateTime end);
    Optional<Transaction> findByExternalReference(String externalReference);
    List<Transaction> findByUserAndAmountAndStatus(User user, BigDecimal amount, String status);
    List<Transaction> findByUserAndAmountAndStatusAndTransactionType(User user, BigDecimal amount, String status, String transactionType);
}
