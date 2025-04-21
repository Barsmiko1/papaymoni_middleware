package com.papaymoni.middleware.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private String transactionType; // DEPOSIT, WITHDRAWAL, FEE
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private String status; // PENDING, COMPLETED, FAILED
    private String externalReference; // Reference from payment provider

    @ManyToOne
    @JoinColumn(name = "virtual_account_id")
    private VirtualAccount virtualAccount;

    private String paymentMethod;
    private String paymentDetails;
    private String receiptUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

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
