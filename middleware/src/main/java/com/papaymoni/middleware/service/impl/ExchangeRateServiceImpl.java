package com.papaymoni.middleware.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.ExchangeRateDto;
import com.papaymoni.middleware.service.ExchangeRateService;
import com.papaymoni.middleware.util.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;

    @Value("${exchange.api.key}")
    private String apiKey;

    @Value("${exchange.api.url}")
    private String apiUrl;

    @Value("${exchange.fee.percentage}")
    private String feePercentageStr;

    private BigDecimal getFeePercentage() {
        return new BigDecimal(feePercentageStr);
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "#baseCurrency + '-' + #targetCurrencies")
    public ExchangeRateDto getExchangeRates(String baseCurrency, List<String> targetCurrencies) {
        try {
            String targetCurrenciesStr = String.join(",", targetCurrencies);
            String url = String.format("%s/fetch-multi?from=%s&to=%s&api_key=%s",
                    apiUrl, baseCurrency, targetCurrenciesStr, apiKey);

            log.info("Fetching exchange rates from {} to {}", baseCurrency, targetCurrenciesStr);

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.addHeader("accept", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    String responseBody = EntityUtils.toString(entity);

                    if (statusCode != 200) {
                        log.error("Error fetching exchange rates: {}", responseBody);
                        // Fallback to our internal converter if API fails
                        return getExchangeRatesFromConverter(baseCurrency, targetCurrencies);
                    }

                    JsonNode root = objectMapper.readTree(responseBody);

                    ExchangeRateDto dto = new ExchangeRateDto();
                    dto.setBaseCurrency(root.get("base").asText());

                    Map<String, BigDecimal> rates = new HashMap<>();
                    JsonNode results = root.get("results");

                    for (String currency : targetCurrencies) {
                        if (results.has(currency)) {
                            rates.put(currency, new BigDecimal(results.get(currency).asText()));
                        }
                    }

                    dto.setRates(rates);
                    dto.setFee(getFeePercentage());
                    dto.setUpdated(root.get("updated").asText());

                    return dto;
                }
            }
        } catch (Exception e) {
            log.error("Error fetching exchange rates", e);
            // Fallback to our internal converter if API fails
            return getExchangeRatesFromConverter(baseCurrency, targetCurrencies);
        }
    }

    @Override
    @Cacheable(value = "exchangeRate", key = "#fromCurrency + '-' + #toCurrency")
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        try {
            if (fromCurrency.equals(toCurrency)) {
                return BigDecimal.ONE;
            }

            String url = String.format("%s/fetch-one?from=%s&to=%s&api_key=%s",
                    apiUrl, fromCurrency, toCurrency, apiKey);

            log.info("Fetching exchange rate from {} to {}", fromCurrency, toCurrency);

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.addHeader("accept", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    String responseBody = EntityUtils.toString(entity);

                    if (statusCode != 200) {
                        log.error("Error fetching exchange rate: {}", responseBody);
                        // Fallback to our internal converter if API fails
                        return currencyConverter.convert(BigDecimal.ONE, fromCurrency, toCurrency);
                    }

                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode result = root.get("result");

                    if (result.has(toCurrency)) {
                        return new BigDecimal(result.get(toCurrency).asText());
                    } else {
                        log.error("Target currency {} not found in response", toCurrency);
                        return currencyConverter.convert(BigDecimal.ONE, fromCurrency, toCurrency);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching exchange rate", e);
            // Fallback to our internal converter if API fails
            return currencyConverter.convert(BigDecimal.ONE, fromCurrency, toCurrency);
        }
    }

    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(getFeePercentage()).setScale(6, RoundingMode.HALF_UP);
    }

    private ExchangeRateDto getExchangeRatesFromConverter(String baseCurrency, List<String> targetCurrencies) {
        ExchangeRateDto dto = new ExchangeRateDto();
        dto.setBaseCurrency(baseCurrency);

        Map<String, BigDecimal> rates = new HashMap<>();
        for (String currency : targetCurrencies) {
            rates.put(currency, currencyConverter.convert(BigDecimal.ONE, baseCurrency, currency));
        }

        dto.setRates(rates);
        dto.setFee(getFeePercentage());
        dto.setUpdated(java.time.LocalDateTime.now().toString());

        return dto;
    }
}
