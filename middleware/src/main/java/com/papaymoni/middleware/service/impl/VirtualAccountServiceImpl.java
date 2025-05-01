package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.VirtualAccountResponse;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.VirtualAccountRepository;
import com.papaymoni.middleware.service.EncryptionService;
import com.papaymoni.middleware.service.PalmpayStaticVirtualAccountService;
import com.papaymoni.middleware.service.VirtualAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class VirtualAccountServiceImpl implements VirtualAccountService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final PalmpayStaticVirtualAccountService palmpayStaticVirtualAccountService;
    private final EncryptionService encryptionService;
    private final CacheManager cacheManager;

    // Cache names
    private static final String USER_ACCOUNTS_CACHE = "userVirtualAccounts";
    private static final String ACCOUNT_CACHE = "virtualAccountById";
    private static final String ACCOUNT_NUMBER_CACHE = "virtualAccountByNumber";

    public VirtualAccountServiceImpl(
            VirtualAccountRepository virtualAccountRepository,
            PalmpayStaticVirtualAccountService palmpayStaticVirtualAccountService,
            EncryptionService encryptionService,
            CacheManager cacheManager) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.palmpayStaticVirtualAccountService = palmpayStaticVirtualAccountService;
        this.encryptionService = encryptionService;
        this.cacheManager = cacheManager;
    }

    @Override
    @Cacheable(value = USER_ACCOUNTS_CACHE, key = "#user.id")
    public List<VirtualAccount> getUserVirtualAccounts(User user) {
        log.debug("Fetching virtual accounts for user ID: {}", user.getId());
        return virtualAccountRepository.findByUser(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_ACCOUNTS_CACHE, key = "#user.id")
    public VirtualAccount createVirtualAccount(User user, String currency) {
        log.info("Creating new virtual account for user ID: {} with currency: {}", user.getId(), currency);

        // Check if BVN is verified
        if (!user.isBvnVerified()) {
            log.warn("Cannot create virtual account - BVN not verified for user: {}", user.getId());
            throw new IllegalStateException("BVN verification is required to create a virtual account");
        }

        // Get decrypted BVN (if encrypted)
        String bvn;
        try {
            if (user.getBvn() != null && user.getBvn().startsWith("ENC:")) {
                bvn = encryptionService.decrypt(user.getBvn().substring(4));
            } else {
                bvn = user.getBvn();
            }
        } catch (Exception e) {
            log.error("Error decrypting BVN for user: {}", user.getId(), e);
            throw new IllegalStateException("Failed to access BVN information");
        }

        try {
            // Call PalmpayStaticVirtualAccountService
            VirtualAccountResponse response = palmpayStaticVirtualAccountService.createVirtualAccount(user, currency);

            // Create new virtual account entity
            VirtualAccount virtualAccount = new VirtualAccount();
            virtualAccount.setUser(user);
            virtualAccount.setAccountNumber(response.getAccountNumber());
            virtualAccount.setBankCode(response.getBankCode());
            virtualAccount.setBankName(response.getBankName());
            virtualAccount.setAccountName(response.getAccountName());
            virtualAccount.setCurrency(currency);
            virtualAccount.setBalance(BigDecimal.ZERO);
            virtualAccount.setActive(true);

            // Save to database
            VirtualAccount savedAccount = virtualAccountRepository.save(virtualAccount);

            // Update cache
            updateAccountCache(savedAccount);

            log.info("Created virtual account: {} for user: {}", savedAccount.getAccountNumber(), user.getId());
            return savedAccount;

        } catch (IOException e) {
            log.error("Failed to create virtual account for user: {}", user.getId(), e);
            throw new RuntimeException("Failed to create virtual account: " + e.getMessage());
        }
    }

    /**
     * Disable a virtual account
     * @param account The account to disable
     * @return true if successful
     */
    @Transactional
    public boolean disableVirtualAccount(VirtualAccount account) {
        try {
            boolean success = palmpayStaticVirtualAccountService.updateVirtualAccountStatus(
                    account.getAccountNumber(), "Disabled");

            if (success) {
                account.setActive(false);
                account.setUpdatedAt(LocalDateTime.now());
                virtualAccountRepository.save(account);

                // Update cache
                updateAccountCache(account);

                log.info("Disabled virtual account: {}", account.getAccountNumber());
            }

            return success;
        } catch (IOException e) {
            log.error("Failed to disable virtual account: {}", account.getAccountNumber(), e);
            throw new RuntimeException("Failed to disable virtual account: " + e.getMessage());
        }
    }

    /**
     * Enable a virtual account
     * @param account The account to enable
     * @return true if successful
     */
    @Transactional
    public boolean enableVirtualAccount(VirtualAccount account) {
        try {
            boolean success = palmpayStaticVirtualAccountService.updateVirtualAccountStatus(
                    account.getAccountNumber(), "Enabled");

            if (success) {
                account.setActive(true);
                account.setUpdatedAt(LocalDateTime.now());
                virtualAccountRepository.save(account);

                // Update cache
                updateAccountCache(account);

                log.info("Enabled virtual account: {}", account.getAccountNumber());
            }

            return success;
        } catch (IOException e) {
            log.error("Failed to enable virtual account: {}", account.getAccountNumber(), e);
            throw new RuntimeException("Failed to enable virtual account: " + e.getMessage());
        }
    }

    /**
     * Delete a virtual account
     * @param account The account to delete
     * @return true if successful
     */
    @Transactional
    public boolean deleteVirtualAccount(VirtualAccount account) {
        try {
            boolean success = palmpayStaticVirtualAccountService.deleteVirtualAccount(
                    account.getAccountNumber());

            if (success) {
                // Remove from cache before deleting
                evictAccountFromCache(account);

                // Delete from database
                virtualAccountRepository.delete(account);

                log.info("Deleted virtual account: {}", account.getAccountNumber());
            }

            return success;
        } catch (IOException e) {
            log.error("Failed to delete virtual account: {}", account.getAccountNumber(), e);
            throw new RuntimeException("Failed to delete virtual account: " + e.getMessage());
        }
    }

    @Override
    @Cacheable(value = ACCOUNT_CACHE, key = "#id")
    public VirtualAccount getVirtualAccountById(Long id) {
        log.debug("Fetching virtual account by ID: {}", id);
        return virtualAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found with id: " + id));
    }

    @Override
    @Cacheable(value = ACCOUNT_NUMBER_CACHE, key = "#accountNumber")
    public VirtualAccount getVirtualAccountByAccountNumber(String accountNumber) {
        log.debug("Fetching virtual account by account number: {}", accountNumber);
        return virtualAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found with account number: " + accountNumber));
    }

    @Override
    @Cacheable(value = USER_ACCOUNTS_CACHE, key = "#user.id + '-' + #currency")
    public List<VirtualAccount> getUserVirtualAccountsByCurrency(User user, String currency) {
        log.debug("Fetching virtual accounts for user ID: {} with currency: {}", user.getId(), currency);
        return virtualAccountRepository.findByUserAndCurrency(user, currency);
    }

    /**
     * Asynchronously create a virtual account
     * @param user User to create account for
     * @param currency Currency of the account
     * @return CompletableFuture containing the created virtual account
     */
    @Async
    public CompletableFuture<VirtualAccount> createVirtualAccountAsync(User user, String currency) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createVirtualAccount(user, currency);
            } catch (Exception e) {
                log.error("Error in async virtual account creation", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Update account balances in bulk
     * @param accountUpdates Map of account IDs to new balances
     */
    @Transactional
    public void updateAccountBalances(Map<Long, BigDecimal> accountUpdates) {
        log.info("Bulk updating balances for {} accounts", accountUpdates.size());

        List<VirtualAccount> accounts = virtualAccountRepository.findAllById(accountUpdates.keySet());
        LocalDateTime now = LocalDateTime.now();

        accounts.forEach(account -> {
            BigDecimal newBalance = accountUpdates.get(account.getId());
            if (newBalance != null) {
                account.setBalance(newBalance);
                account.setUpdatedAt(now);
            }
        });

        virtualAccountRepository.saveAll(accounts);

        // Update cache for each account
        accounts.forEach(this::updateAccountCache);
    }

    /**
     * Scheduled task to check and update virtual account statuses
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void refreshVirtualAccountStatus() {
        log.info("Running scheduled virtual account refresh");

        // In a real implementation, this would check account statuses with Palmpay
        // For now, we'll just log
        log.info("Virtual account refresh completed");
    }

    /**
     * Update account balance
     * @param account The account to update
     * @param newBalance The new balance
     * @return The updated account
     */
    @Transactional
    public VirtualAccount updateAccountBalance(VirtualAccount account, BigDecimal newBalance) {
        account.setBalance(newBalance);
        account.setUpdatedAt(LocalDateTime.now());

        VirtualAccount updatedAccount = virtualAccountRepository.save(account);
        updateAccountCache(updatedAccount);

        log.info("Updated balance for account: {} to: {}", account.getAccountNumber(), newBalance);
        return updatedAccount;
    }

    /**
     * Helper method to update account in all caches
     */
    private void updateAccountCache(VirtualAccount account) {
        cacheManager.getCache(ACCOUNT_CACHE).put(account.getId(), account);
        cacheManager.getCache(ACCOUNT_NUMBER_CACHE).put(account.getAccountNumber(), account);
        // We'll evict the user accounts cache to force a refresh
        cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId());
        cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId() + "-" + account.getCurrency());
    }

    /**
     * Helper method to evict account from all caches
     */
    private void evictAccountFromCache(VirtualAccount account) {
        cacheManager.getCache(ACCOUNT_CACHE).evict(account.getId());
        cacheManager.getCache(ACCOUNT_NUMBER_CACHE).evict(account.getAccountNumber());
        cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId());
        cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId() + "-" + account.getCurrency());
    }
}