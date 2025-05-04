package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Optimized queries with JOIN FETCH
    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.user " +
            "LEFT JOIN FETCH t.virtualAccount va " +
            "LEFT JOIN FETCH va.user " +
            "WHERE t.user.id = :userId " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findByUserWithFetch(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t " +
            "LEFT JOIN FETCH t.user " +
            "LEFT JOIN FETCH t.virtualAccount va " +
            "LEFT JOIN FETCH va.user " +
            "WHERE t.id = :id")
    Optional<Transaction> findByIdWithFetch(@Param("id") Long id);

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.user " +
            "LEFT JOIN FETCH t.virtualAccount va " +
            "LEFT JOIN FETCH va.user " +
            "WHERE t.user.id = :userId " +
            "AND t.createdAt BETWEEN :start AND :end " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findByUserAndCreatedAtBetweenWithFetch(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}