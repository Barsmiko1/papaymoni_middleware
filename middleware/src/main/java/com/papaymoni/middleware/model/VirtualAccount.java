// Update VirtualAccount.java
package com.papaymoni.middleware.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    private String accountNumber;
    private String bankCode;
    private String bankName;
    private String accountName;
    private String currency;
    private BigDecimal balance;
    private boolean active;

    @OneToMany(mappedBy = "virtualAccount")
    @JsonIgnore
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

    // Override toString method to avoid circular references
    @Override
    public String toString() {
        return "VirtualAccount{" +
                "id=" + id +
                ", accountNumber='" + accountNumber + '\'' +
                ", bankCode='" + bankCode + '\'' +
                ", bankName='" + bankName + '\'' +
                ", accountName='" + accountName + '\'' +
                ", currency='" + currency + '\'' +
                ", balance=" + balance +
                ", active=" + active +
                ", userId=" + (user != null ? user.getId() : null) +
                '}';
    }
}
