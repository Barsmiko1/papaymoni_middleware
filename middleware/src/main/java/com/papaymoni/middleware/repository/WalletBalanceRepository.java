package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {
    List<WalletBalance> findByUser(User user);
    Optional<WalletBalance> findByUserAndCurrency(User user, String currency);
}
