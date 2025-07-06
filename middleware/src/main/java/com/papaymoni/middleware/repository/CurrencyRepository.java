package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    // Make sure any custom save methods preserve the createdBy field
    List<Currency> findByActiveTrue();
    Optional<Currency> findByCode(String code);
}
