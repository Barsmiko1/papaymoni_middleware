package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.CurrencyDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.repository.CurrencyRepository;
import com.papaymoni.middleware.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl implements CurrencyService {

    private final CurrencyRepository currencyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CurrencyDto> getAllCurrencies() {
        return currencyRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurrencyDto> getActiveCurrencies() {
        return currencyRepository.findByActiveTrue().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "currencies", key = "#code")
    public CurrencyDto getCurrencyByCode(String code) {
        Currency currency = currencyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found with code: " + code));
        return convertToDto(currency);
    }

    @Override
    @Transactional(readOnly = true)
    public Currency getCurrencyEntityByCode(String code) {
        return currencyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found with code: " + code));
    }

    @Override
    @Transactional
    @CacheEvict(value = "currencies", allEntries = true)
    public CurrencyDto createCurrency(CurrencyDto currencyDto, String createdBy) {
        Currency currency = new Currency();
        currency.setCode(currencyDto.getCode().toUpperCase());
        currency.setName(currencyDto.getName());
        currency.setSymbol(currencyDto.getSymbol());
        currency.setDecimalPlaces(currencyDto.getDecimalPlaces());
        currency.setActive(currencyDto.getActive());
        currency.setCreatedBy(createdBy);

        Currency savedCurrency = currencyRepository.save(currency);
        log.info("Created new currency: {}", savedCurrency.getCode());
        return convertToDto(savedCurrency);
    }

    @Override
    @Transactional
    @CacheEvict(value = "currencies", key = "#code")
    public CurrencyDto updateCurrency(String code, CurrencyDto currencyDto, String updatedBy) {
        Currency currency = currencyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found with code: " + code));

        currency.setName(currencyDto.getName());
        currency.setSymbol(currencyDto.getSymbol());
        currency.setDecimalPlaces(currencyDto.getDecimalPlaces());
        currency.setActive(currencyDto.getActive());
        currency.setUpdatedBy(updatedBy);

        Currency updatedCurrency = currencyRepository.save(currency);
        log.info("Updated currency: {}", updatedCurrency.getCode());
        return convertToDto(updatedCurrency);
    }

    @Override
    @Transactional
    @CacheEvict(value = "currencies", key = "#code")
    public void toggleCurrencyStatus(String code, boolean active, String updatedBy) {
        Currency currency = currencyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found with code: " + code));

        currency.setActive(active);
        currency.setUpdatedBy(updatedBy);

        currencyRepository.save(currency);
        log.info("Currency {} status set to {}", code, active ? "active" : "inactive");
    }

    private CurrencyDto convertToDto(Currency currency) {
        CurrencyDto dto = new CurrencyDto();
        dto.setId(currency.getId());
        dto.setCode(currency.getCode());
        dto.setName(currency.getName());
        dto.setSymbol(currency.getSymbol());
        dto.setDecimalPlaces(currency.getDecimalPlaces());
        dto.setActive(currency.getActive());
        return dto;
    }
}
