package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    Page<WalletTransaction> findByUser(User user, Pageable pageable);
    Page<WalletTransaction> findByUserAndCurrency(User user, Currency currency, Pageable pageable);
}
