package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByBvn(String bvn);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByBvn(String bvn);
    Optional<User> findByReferralCode(String referralCode);

    @Query("SELECT u FROM User u WHERE u.username = :username OR u.email = :email")
    Optional<User> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    // New method with join fetch to eagerly load virtual accounts in a single query
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.virtualAccounts WHERE u.username = :username")
    Optional<User> findByUsernameWithVirtualAccount(@Param("username") String username);
}