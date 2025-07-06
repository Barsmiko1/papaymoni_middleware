package com.papaymoni.middleware.util;
import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyInitializer implements ApplicationRunner {

    private final CurrencyRepository currencyRepository;
    private final CacheManager cacheManager; // Inject the CacheManager

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Initializing currencies...");
        initializeCurrencies();

        // Clear any existing cache for currencies
        if (cacheManager != null && cacheManager.getCache("currencies") != null) {
            cacheManager.getCache("currencies").clear();
            log.info("Cleared currency cache");
        }
    }

    @Transactional
    public void initializeCurrencies() {
        // Define currencies with all necessary properties
        Map<String, Object[]> currencyData = new HashMap<>();

        // Format: code, name, symbol, decimalPlaces
        currencyData.put("USD", new Object[]{"United States Dollar", "$", 2});
        currencyData.put("NGN", new Object[]{"Nigerian Naira", "₦", 2});
        currencyData.put("USDT", new Object[]{"Tether USD", "₮", 2});
        currencyData.put("EUR", new Object[]{"Euro", "€", 2});
        currencyData.put("GBP", new Object[]{"British Pound Sterling", "£", 2});

        for (Map.Entry<String, Object[]> entry : currencyData.entrySet()) {
            String code = entry.getKey();
            Object[] data = entry.getValue();

            try {
                if (!currencyRepository.findByCode(code).isPresent()) {
                    Currency currency = new Currency();
                    currency.setCode(code);
                    currency.setName((String) data[0]);
                    currency.setSymbol((String) data[1]);
                    currency.setDecimalPlaces((Integer) data[2]);
                    currency.setActive(true);
                    currency.setCreatedBy("SYSTEM");

                    currencyRepository.save(currency);
                    log.info("Added currency: {}", code);
                } else {
                    log.info("Currency already exists: {}", code);
                }
            } catch (Exception e) {
                log.error("Error initializing currency {}: {}", code, e.getMessage(), e);
            }
        }
        log.info("Currency initialization completed");
    }

    // Add a public method to reinitialize the currencies on demand
    @Transactional
    public void reinitializeCurrencies() {
        initializeCurrencies();

        // Clear any existing cache for currencies
        if (cacheManager != null && cacheManager.getCache("currencies") != null) {
            cacheManager.getCache("currencies").clear();
            log.info("Cleared currency cache");
        }
    }
}