package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.GLEntry;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface GLEntryRepository extends JpaRepository<GLEntry, Long> {
    List<GLEntry> findByUser(User user);
    List<GLEntry> findByTransaction(Transaction transaction);

    @Query("SELECT SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END) FROM GLEntry e WHERE e.user = ?1")
    BigDecimal getUserBalance(User user);

    @Query("SELECT SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END) FROM GLEntry e WHERE e.user = ?1 AND e.currency = ?2")
    BigDecimal getUserBalanceByCurrency(User user, String currency);
}
