package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WalletBalanceDto {
    private Long id;
    private Long userId;
    private String username;
    private CurrencyDto currency;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalBalance;
    private LocalDateTime updatedAt;
}
