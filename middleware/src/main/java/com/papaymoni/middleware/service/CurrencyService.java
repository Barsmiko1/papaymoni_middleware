package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.CurrencyDto;
import com.papaymoni.middleware.model.Currency;

import java.util.List;

public interface CurrencyService {
    List<CurrencyDto> getAllCurrencies();
    List<CurrencyDto> getActiveCurrencies();
    CurrencyDto getCurrencyByCode(String code);
    Currency getCurrencyEntityByCode(String code);
    CurrencyDto createCurrency(CurrencyDto currencyDto, String createdBy);
    CurrencyDto updateCurrency(String code, CurrencyDto currencyDto, String updatedBy);
    void toggleCurrencyStatus(String code, boolean active, String updatedBy);
}
