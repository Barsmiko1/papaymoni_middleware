package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ExchangeRateResponseDto {
    private String base;
    private Map<String, BigDecimal> results;
    private String updated;
    private int ms;
}
