package com.papaymoni.middleware.model;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "wallet_balances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "currency_id"}, name = "UKjh4oxjd16frybwxj482dqmuba")
})
public class WalletBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;


    @Column(name = "available_balance", precision = 18, scale = 8, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "frozen_balance", precision = 18, scale = 8, nullable = false)
    private BigDecimal frozenBalance;

    @Column(name = "total_balance", precision = 18, scale = 8, nullable = false)
    private BigDecimal totalBalance;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
