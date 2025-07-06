package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ExchangeRateDto {
    private String baseCurrency;
    private Map<String, BigDecimal> rates;
    private BigDecimal fee;
    private String updated;
}
