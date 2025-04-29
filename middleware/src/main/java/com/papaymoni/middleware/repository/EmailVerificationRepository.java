package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByEmailAndVerifiedFalse(String email);
    boolean existsByEmailAndVerifiedTrue(String email);
}