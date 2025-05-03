package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {

    @Query("SELECT v FROM VirtualAccount v JOIN FETCH v.user WHERE v.user.id = :userId")
    List<VirtualAccount> findByUserIdWithUser(@Param("userId") Long userId);

    @Query("SELECT v FROM VirtualAccount v JOIN FETCH v.user WHERE v.accountNumber = :accountNumber")
    Optional<VirtualAccount> findByAccountNumberWithUser(@Param("accountNumber") String accountNumber);

    @Query("SELECT v FROM VirtualAccount v JOIN FETCH v.user WHERE v.user.id = :userId AND v.currency = :currency")
    List<VirtualAccount> findByUserIdAndCurrencyWithUser(@Param("userId") Long userId, @Param("currency") String currency);

    // Keep existing methods as fallback
    List<VirtualAccount> findByUserId(Long userId);
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);
    List<VirtualAccount> findByUserIdAndCurrency(Long userId, String currency);
}