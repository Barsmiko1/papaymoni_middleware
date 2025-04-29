package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.BvnVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BvnVerificationRepository extends JpaRepository<BvnVerification, Long> {
    Optional<BvnVerification> findByBvn(String bvn);
    boolean existsByBvnAndVerifiedTrue(String bvn);
}