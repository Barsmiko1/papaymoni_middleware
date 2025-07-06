package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ExchangeRateDto;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeRateService {
    /**
     * Get exchange rates for a base currency to multiple target currencies
     * @param baseCurrency The base currency code
     * @param targetCurrencies List of target currency codes
     * @return ExchangeRateDto containing the rates
     */
    ExchangeRateDto getExchangeRates(String baseCurrency, List<String> targetCurrencies);

    /**
     * Get exchange rate from one currency to another
     * @param fromCurrency The source currency code
     * @param toCurrency The target currency code
     * @return The exchange rate
     */
    BigDecimal getExchangeRate(String fromCurrency, String toCurrency);

    /**
     * Calculate exchange fee based on amount
     * @param amount The amount to calculate fee for
     * @return The fee amount
     */
    BigDecimal calculateFee(BigDecimal amount);
}
