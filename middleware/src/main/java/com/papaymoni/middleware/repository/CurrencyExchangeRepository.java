package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.CurrencyExchange;
import com.papaymoni.middleware.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CurrencyExchangeRepository extends JpaRepository<CurrencyExchange, Long> {
    Page<CurrencyExchange> findByUser(User user, Pageable pageable);
    List<CurrencyExchange> findTop10ByUserOrderByCreatedAtDesc(User user);
}
