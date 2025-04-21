package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {
    List<VirtualAccount> findByUser(User user);
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);
    List<VirtualAccount> findByUserAndCurrency(User user, String currency);
    List<VirtualAccount> findByUserAndActive(User user, boolean active);
}
