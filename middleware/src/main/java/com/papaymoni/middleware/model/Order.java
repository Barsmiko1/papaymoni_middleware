package com.papaymoni.middleware.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String id; // Bybit order ID

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String bybitItemId;
    private String tokenId; // e.g., USDT
    private String currencyId; // e.g., EUR
    private int side; // 0: buy, 1: sell
    private String orderType;
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal fee;
    private int status;
    private String targetUserId;
    private String targetNickName;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Transaction transaction;

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
