package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ExchangeRateDto;
import com.papaymoni.middleware.dto.ExchangeRequestDto;
import com.papaymoni.middleware.dto.ExchangeResultDto;
import com.papaymoni.middleware.model.User;

import java.util.List;

public interface CurrencyExchangeService {
    /**
     * Get exchange rates for a base currency to all supported currencies
     * @param baseCurrency The base currency code
     * @return ExchangeRateDto containing the rates
     */
    ExchangeRateDto getExchangeRates(String baseCurrency);

    /**
     * Process a currency exchange for a user
     * @param user The user performing the exchange
     * @param request The exchange request details
     * @param ipAddress The IP address of the user
     * @return ExchangeResultDto with the result of the exchange
     */
    ExchangeResultDto exchangeCurrency(User user, ExchangeRequestDto request, String ipAddress);

    /**
     * Get recent exchanges for a user
     * @param user The user
     * @return List of recent exchanges
     */
    List<ExchangeResultDto> getRecentExchanges(User user);
}
