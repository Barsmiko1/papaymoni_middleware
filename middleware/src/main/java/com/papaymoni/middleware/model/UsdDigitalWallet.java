package com.papaymoni.middleware.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "digital_wallets")
public class UsdDigitalWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    private String walletUuid;
    private String uuid;
    private String address;
    private String network;
    private String currency;
    private String qrCodeImage;
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Override toString method to avoid circular references
    @Override
    public String toString() {
        return "DigitalWallet{" +
                "id=" + id +
                ", walletUuid='" + walletUuid + '\'' +
                ", uuid='" + uuid + '\'' +
                ", address='" + address + '\'' +
                ", network='" + network + '\'' +
                ", currency='" + currency + '\'' +
                ", active=" + active +
                ", userId=" + (user != null ? user.getId() : null) +
                '}';
    }
}
