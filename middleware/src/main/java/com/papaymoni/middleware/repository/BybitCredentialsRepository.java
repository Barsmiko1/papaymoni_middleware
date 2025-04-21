package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.BybitCredentials;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BybitCredentialsRepository extends JpaRepository<BybitCredentials, Long> {
    Optional<BybitCredentials> findByUser(User user);
    boolean existsByUser(User user);
}
