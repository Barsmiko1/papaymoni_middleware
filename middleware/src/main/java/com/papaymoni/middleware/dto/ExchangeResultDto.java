package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExchangeResultDto {
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
    private BigDecimal rate;
    private BigDecimal fee;
    private BigDecimal totalDeducted;
    private String receiptUrl;
    private LocalDateTime exchangedAt;
}
