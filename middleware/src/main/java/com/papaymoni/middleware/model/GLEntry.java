package com.papaymoni.middleware.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "gl_entries")
public class GLEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String entryType; // DEBIT, CREDIT
    private String accountType; // USER, PLATFORM, FEE
    private BigDecimal amount;
    private String currency;
    private String description;

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
