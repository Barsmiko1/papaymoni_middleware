package com.papaymoni.middleware.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashbackEvent implements Serializable {
    private Long userId;
    private BigDecimal cashbackAmount;
    private String currency;
    private String originalTransactionType;
    private BigDecimal originalTransactionAmount;
}

