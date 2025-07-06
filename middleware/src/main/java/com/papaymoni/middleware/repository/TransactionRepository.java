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
    List<Transaction> findByUserOrderByCreatedAtDesc(User user);
    Optional<Transaction> findByExternalReference(String externalReference);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.paymentMethod = :paymentMethod")
    List<Transaction> findByStatusAndPaymentMethod(@Param("status") String status, @Param("paymentMethod") String paymentMethod);

    boolean existsByExternalReference(String externalReference);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.order LEFT JOIN FETCH t.virtualAccount WHERE t.id = :id")
    Optional<Transaction> findByIdWithFullFetch(@Param("id") Long id);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.order LEFT JOIN FETCH t.virtualAccount WHERE t.user.id = :userId")
    List<Transaction> findByUserWithFullFetch(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.order LEFT JOIN FETCH t.virtualAccount WHERE t.user.id = :userId AND t.createdAt BETWEEN :start AND :end")
    List<Transaction> findByUserAndCreatedAtBetweenWithFetch(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Transaction> findByUserAndAmountAndStatus(User user, BigDecimal amount, String status);

    @Query("SELECT t.id FROM Transaction t WHERE t.status = :status AND t.paymentMethod = :paymentMethod")
    List<Long> findIdsByStatusAndPaymentMethod(String status, String paymentMethod);

    //boolean existsByExternalReference(String externalReference);

    //Optional<Transaction> findByExternalReference(String orderNo);
}
