package com.papaymoni.middleware.dto;

import com.papaymoni.middleware.model.WalletTransaction.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WalletTransactionDto {
    private Long id;
    private Long userId;
    private String username;
    private CurrencyDto currency;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String description;
    private String referenceId;
    private String referenceType;
    private String createdBy;
    private LocalDateTime createdAt;
}
