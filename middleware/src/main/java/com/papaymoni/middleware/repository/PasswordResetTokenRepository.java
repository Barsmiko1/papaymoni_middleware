package com.papaymoni.middleware.repository;

import com.papaymoni.middleware.model.PasswordResetToken;
import com.papaymoni.middleware.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    List<PasswordResetToken> findByUser(User user);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user = :user AND t.expiryDate > :now AND t.used = false")
    Optional<PasswordResetToken> findValidTokenForUser(@Param("user") User user, @Param("now") LocalDateTime now);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.token = :token AND t.expiryDate > :now AND t.used = false")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);
}
