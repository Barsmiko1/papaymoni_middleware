package com.papaymoni.middleware.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Entity
@Table(name = "virtual_accounts")
public class VirtualAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String accountNumber;
    private String bankCode;
    private String bankName;
    private String accountName;
    private String currency;
    private BigDecimal balance;
    private boolean active;

    @OneToMany(mappedBy = "virtualAccount")
    private Set<Transaction> transactions;

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
}
