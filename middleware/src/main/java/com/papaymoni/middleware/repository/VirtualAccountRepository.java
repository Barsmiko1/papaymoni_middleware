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
    List<VirtualAccount> findByUser(User user);
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);
    List<VirtualAccount> findByUserAndCurrency(User user, String currency);
    List<VirtualAccount> findByUserAndActive(User user, boolean active);

    // Added optimized queries
    @Query("SELECT v FROM VirtualAccount v WHERE v.user.id = :userId")
    List<VirtualAccount> findByUserId(@Param("userId") Long userId);

    @Query("SELECT v FROM VirtualAccount v WHERE v.user.id IN :userIds")
    List<VirtualAccount> findByUserIdIn(@Param("userIds") List<Long> userIds);

    @Query("SELECT v FROM VirtualAccount v WHERE v.createdAt > :cutoffDate")
    List<VirtualAccount> findByCreatedAtAfter(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT v FROM VirtualAccount v JOIN FETCH v.user WHERE v.accountNumber = :accountNumber")
    Optional<VirtualAccount> findByAccountNumberWithUser(@Param("accountNumber") String accountNumber);

    @Query(value = "SELECT * FROM virtual_accounts WHERE user_id = :userId ORDER BY created_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<VirtualAccount> findLatestByUserId(@Param("userId") Long userId);
}