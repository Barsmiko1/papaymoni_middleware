package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.VirtualAccountResponse;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.VirtualAccountRepository;
import com.papaymoni.middleware.service.PalmpayStaticVirtualAccountService;
import com.papaymoni.middleware.service.VirtualAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualAccountServiceImpl implements VirtualAccountService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final PalmpayStaticVirtualAccountService palmpayStaticVirtualAccountService;

    @Override
    @Cacheable(value = "userVirtualAccounts", key = "#user.id")
    public List<VirtualAccount> getUserVirtualAccounts(User user) {
        log.debug("Fetching virtual accounts for user ID: {}", user.getId());
        return virtualAccountRepository.findByUser(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userVirtualAccounts", key = "#user.id")
    public VirtualAccount createVirtualAccount(User user, String currency) {
        log.debug("Creating new virtual account for user ID: {} with currency: {}", user.getId(), currency);

        // Call third-party provider to create virtual account
        VirtualAccountResponse response = palmpayStaticVirtualAccountService.createVirtualAccount(user, currency);

        // Create and save virtual account in our system
        VirtualAccount virtualAccount = new VirtualAccount();
        virtualAccount.setUser(user);
        virtualAccount.setAccountNumber(response.getAccountNumber());
        virtualAccount.setBankCode(response.getBankCode());
        virtualAccount.setBankName(response.getBankName());
        virtualAccount.setAccountName(response.getAccountName());
        virtualAccount.setCurrency(currency);
        virtualAccount.setBalance(BigDecimal.ZERO);
        virtualAccount.setActive(true);

        return virtualAccountRepository.save(virtualAccount);
    }

    @Override
    @Cacheable(value = "virtualAccountById", key = "#id")
    public VirtualAccount getVirtualAccountById(Long id) {
        log.debug("Fetching virtual account by ID: {}", id);
        return virtualAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found with id: " + id));
    }

    @Override
    @Cacheable(value = "virtualAccountByNumber", key = "#accountNumber")
    public VirtualAccount getVirtualAccountByAccountNumber(String accountNumber) {
        log.debug("Fetching virtual account by account number: {}", accountNumber);
        return virtualAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found with account number: " + accountNumber));
    }

    @Override
    @Cacheable(value = "userVirtualAccountsByCurrency", key = "#user.id + '-' + #currency")
    public List<VirtualAccount> getUserVirtualAccountsByCurrency(User user, String currency) {
        log.debug("Fetching virtual accounts for user ID: {} with currency: {}", user.getId(), currency);
        return virtualAccountRepository.findByUserAndCurrency(user, currency);
    }
}