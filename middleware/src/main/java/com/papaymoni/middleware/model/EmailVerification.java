package com.papaymoni.middleware.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "email_verifications")
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    private boolean verified;

    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        if (expiryTime == null) {
            // OTP valid for 15 minutes
            expiryTime = LocalDateTime.now().plusMinutes(15);
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
}