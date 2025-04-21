package com.papaymoni.middleware.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
public class CurrencyConverter {

    // Mock exchange rates (in a real implementation, these would be fetched from an API)
    private static final Map<String, BigDecimal> EXCHANGE_RATES = new HashMap<>();

    static {
        // Base currency is NGN
        EXCHANGE_RATES.put("NGN", BigDecimal.ONE);
        EXCHANGE_RATES.put("USD", new BigDecimal("0.00065"));
        EXCHANGE_RATES.put("EUR", new BigDecimal("0.00060"));
        EXCHANGE_RATES.put("GBP", new BigDecimal("0.00052"));
        EXCHANGE_RATES.put("USDT", new BigDecimal("0.00065"));
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        // Convert from source currency to NGN
        BigDecimal amountInNGN = amount.divide(EXCHANGE_RATES.get(fromCurrency), 6, RoundingMode.HALF_UP);

        // Convert from NGN to target currency
        return amountInNGN.multiply(EXCHANGE_RATES.get(toCurrency)).setScale(6, RoundingMode.HALF_UP);
    }
}
