package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.VirtualAccountResponseDto;
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
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class VirtualAccountServiceImpl implements VirtualAccountService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final PalmpayStaticVirtualAccountService palmpayStaticVirtualAccountService;
    private final EncryptionService encryptionService;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;

    // Cache names
    private static final String USER_ACCOUNTS_CACHE = "userVirtualAccounts";
    private static final String ACCOUNT_CACHE = "virtualAccountById";
    private static final String ACCOUNT_NUMBER_CACHE = "virtualAccountByNumber";

    // Metrics for monitoring
    private final ConcurrentHashMap<String, AtomicInteger> operationCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> operationErrors = new ConcurrentHashMap<>();

    public VirtualAccountServiceImpl(
            VirtualAccountRepository virtualAccountRepository,
            PalmpayStaticVirtualAccountService palmpayStaticVirtualAccountService,
            EncryptionService encryptionService,
            CacheManager cacheManager,
            TransactionTemplate transactionTemplate) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.palmpayStaticVirtualAccountService = palmpayStaticVirtualAccountService;
        this.encryptionService = encryptionService;
        this.cacheManager = cacheManager;
        this.transactionTemplate = transactionTemplate;

        // Initialize operation counters
        operationCounts.put("getUserAccounts", new AtomicInteger(0));
        operationCounts.put("createAccount", new AtomicInteger(0));
        operationCounts.put("updateBalance", new AtomicInteger(0));
        operationCounts.put("getAccountById", new AtomicInteger(0));
        operationCounts.put("getAccountByNumber", new AtomicInteger(0));

        operationErrors.put("getUserAccounts", new AtomicInteger(0));
        operationErrors.put("createAccount", new AtomicInteger(0));
        operationErrors.put("updateBalance", new AtomicInteger(0));
        operationErrors.put("getAccountById", new AtomicInteger(0));
        operationErrors.put("getAccountByNumber", new AtomicInteger(0));
    }

    @Override
    @Cacheable(value = USER_ACCOUNTS_CACHE, key = "#user.id", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<VirtualAccount> getUserVirtualAccounts(User user) {
        operationCounts.get("getUserAccounts").incrementAndGet();
        log.debug("Fetching virtual accounts for user ID: {}", user.getId());

        try {
            // Use the improved method with JOIN FETCH to avoid N+1 queries
            return virtualAccountRepository.findByUserIdWithJoinFetch(user.getId());
        } catch (Exception e) {
            operationErrors.get("getUserAccounts").incrementAndGet();
            log.error("Error fetching virtual accounts for user: {}", user.getId(), e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = USER_ACCOUNTS_CACHE, key = "#user.id")
    public VirtualAccount createVirtualAccount(User user, String currency) {
        operationCounts.get("createAccount").incrementAndGet();
        log.info("Creating new virtual account for user ID: {} with currency: {}", user.getId(), currency);

        try {
            // Check if BVN is verified
            if (!user.isBvnVerified()) {
                log.warn("Cannot create virtual account - BVN not verified for user: {}", user.getId());
                throw new IllegalStateException("BVN verification is required to create a virtual account");
            }

            // Get decrypted BVN (if encrypted)
            String bvn = getBvnSafely(user);

            // Call PalmpayStaticVirtualAccountService
            VirtualAccountResponseDto response = palmpayStaticVirtualAccountService.createVirtualAccount(user, currency);

            // Create new virtual account entity within transaction
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
            operationErrors.get("createAccount").incrementAndGet();
            log.error("Error from Palmpay service while creating virtual account for user: {}", user.getId(), e);
            throw new RuntimeException("Failed to create virtual account: " + e.getMessage(), e);
        } catch (Exception e) {
            operationErrors.get("createAccount").incrementAndGet();
            log.error("Unexpected error creating virtual account for user: {}", user.getId(), e);
            throw new RuntimeException("Failed to create virtual account due to an unexpected error", e);
        }
    }

    /**
     * Safely get and decrypt BVN from user
     * @param user The user to get BVN from
     * @return Decrypted BVN
     */
    private String getBvnSafely(User user) {
        if (user.getBvn() == null) {
            throw new IllegalArgumentException("User does not have a BVN");
        }

        try {
            if (user.getBvn().startsWith("ENC:")) {
                return encryptionService.decrypt(user.getBvn());
            } else {
                return user.getBvn();
            }
        } catch (Exception e) {
            log.error("Error decrypting BVN for user: {}", user.getId(), e);
            throw new IllegalStateException("Failed to access BVN information", e);
        }
    }

    @Override
    @Cacheable(value = ACCOUNT_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public VirtualAccount getVirtualAccountById(Long id) {
        operationCounts.get("getAccountById").incrementAndGet();
        log.debug("Fetching virtual account by ID: {}", id);

        try {
            return virtualAccountRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found with id: " + id));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            operationErrors.get("getAccountById").incrementAndGet();
            log.error("Error fetching virtual account by ID: {}", id, e);
            throw e;
        }
    }

    @Override
    @Cacheable(value = ACCOUNT_NUMBER_CACHE, key = "#accountNumber")
    @Transactional(readOnly = true)
    public VirtualAccount getVirtualAccountByAccountNumber(String accountNumber) {
        operationCounts.get("getAccountByNumber").incrementAndGet();
        log.debug("Fetching virtual account by account number: {}", accountNumber);

        try {
            // Use the improved method with JOIN FETCH to avoid N+1 queries
            return virtualAccountRepository.findByAccountNumberWithJoinFetch(accountNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found with account number: " + accountNumber));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            operationErrors.get("getAccountByNumber").incrementAndGet();
            log.error("Error fetching virtual account by account number: {}", accountNumber, e);
            throw e;
        }
    }

    @Override
    @Cacheable(value = USER_ACCOUNTS_CACHE, key = "#user.id + '-' + #currency", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<VirtualAccount> getUserVirtualAccountsByCurrency(User user, String currency) {
        log.debug("Fetching virtual accounts for user ID: {} with currency: {}", user.getId(), currency);

        try {
            // Use the improved method with JOIN FETCH to avoid N+1 queries
            return virtualAccountRepository.findByUserIdAndCurrencyWithUser(user.getId(), currency);
        } catch (Exception e) {
            log.error("Error fetching virtual accounts by currency for user: {}", user.getId(), e);
            throw e;
        }
    }

    /**
     * Asynchronously create a virtual account
     * @param user User to create account for
     * @param currency Currency of the account
     * @return CompletableFuture containing the created virtual account
     */
    @Override
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
     * Update account balances in bulk with proper transaction management
     * @param accountUpdates Map of account IDs to new balances
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public void updateAccountBalances(Map<Long, BigDecimal> accountUpdates) {
        log.info("Bulk updating balances for {} accounts", accountUpdates.size());

        try {
            List<VirtualAccount> accounts = virtualAccountRepository.findAllById(accountUpdates.keySet());
            LocalDateTime now = LocalDateTime.now();

            // Pre-validate all updates before making changes
            for (VirtualAccount account : accounts) {
                BigDecimal newBalance = accountUpdates.get(account.getId());
                if (newBalance == null) {
                    log.warn("Account ID {} included in update but no balance provided", account.getId());
                    continue;
                }

                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Cannot set negative balance for account: " + account.getId());
                }
            }

            // Apply updates after validation
            for (VirtualAccount account : accounts) {
                BigDecimal newBalance = accountUpdates.get(account.getId());
                if (newBalance != null) {
                    account.setBalance(newBalance);
                    account.setUpdatedAt(now);
                }
            }

            // Save all changes in a single transaction
            List<VirtualAccount> savedAccounts = virtualAccountRepository.saveAll(accounts);

            // Update cache for each account after successful transaction
            savedAccounts.forEach(this::updateAccountCache);

            log.info("Successfully updated balances for {} accounts", savedAccounts.size());
        } catch (Exception e) {
            log.error("Error during bulk account balance update", e);
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    /**
     * Scheduled task to check and update virtual account statuses
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void refreshVirtualAccountStatus() {
        log.info("Running scheduled virtual account refresh");

        try {
            // In a real implementation, this would check account statuses with Palmpay
            // For now, we'll just log
            log.info("Virtual account refresh completed");
        } catch (Exception e) {
            log.error("Error during scheduled virtual account refresh", e);
        }
    }

    /**
     * Update account balance with proper transaction management
     * @param account The account to update
     * @param newBalance The new balance
     * @return The updated account
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public VirtualAccount updateAccountBalance(VirtualAccount account, BigDecimal newBalance) {
        operationCounts.get("updateBalance").incrementAndGet();

        // Validate inputs
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        if (newBalance == null) {
            throw new IllegalArgumentException("Balance cannot be null");
        }

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        log.info("Updating balance for account ID: {} from {} to {}",
                account.getId(), account.getBalance(), newBalance);

        try {
            // Re-fetch account to ensure we have the most recent version
            VirtualAccount currentAccount = virtualAccountRepository.findById(account.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + account.getId()));

            // Update the balance
            currentAccount.setBalance(newBalance);
            currentAccount.setUpdatedAt(LocalDateTime.now());

            // Save changes
            VirtualAccount updatedAccount = virtualAccountRepository.save(currentAccount);

            // Update cache after successful transaction
            updateAccountCache(updatedAccount);

            log.info("Successfully updated balance for account: {}", updatedAccount.getAccountNumber());
            return updatedAccount;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            operationErrors.get("updateBalance").incrementAndGet();
            log.error("Error updating balance for account: {}", account.getId(), e);
            throw new RuntimeException("Failed to update account balance", e);
        }
    }

    /**
     * Helper method to update account in all caches with retry logic
     */
    private void updateAccountCache(VirtualAccount account) {
        try {
            // Retry up to 3 times to ensure cache is updated
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    cacheManager.getCache(ACCOUNT_CACHE).put(account.getId(), account);
                    cacheManager.getCache(ACCOUNT_NUMBER_CACHE).put(account.getAccountNumber(), account);
                    // We'll evict the user accounts cache to force a refresh
                    cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId());
                    cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId() + "-" + account.getCurrency());
                    return;
                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        throw e;
                    }
                    log.warn("Cache update failed (attempt {}), retrying...", attempt, e);
                    Thread.sleep(100 * attempt);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update cache for account: {}", account.getId(), e);
            // Continue execution - cache failure shouldn't stop business operations
        }
    }

    /**
     * Helper method to evict account from all caches with retry logic
     */
    private void evictAccountFromCache(VirtualAccount account) {
        try {
            // Retry up to 3 times to ensure cache is evicted
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    cacheManager.getCache(ACCOUNT_CACHE).evict(account.getId());
                    cacheManager.getCache(ACCOUNT_NUMBER_CACHE).evict(account.getAccountNumber());
                    cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId());
                    cacheManager.getCache(USER_ACCOUNTS_CACHE).evict(account.getUser().getId() + "-" + account.getCurrency());
                    return;
                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        throw e;
                    }
                    log.warn("Cache eviction failed (attempt {}), retrying...", attempt, e);
                    Thread.sleep(100 * attempt);
                }
            }
        } catch (Exception e) {
            log.error("Failed to evict cache for account: {}", account.getId(), e);
            // Continue execution - cache failure shouldn't stop business operations
        }
    }

    /**
     * Get account count for health checks
     * @return Count of accounts
     */
    @Override
    @Transactional(readOnly = true)
    public long getAccountCount() {
        try {
            return virtualAccountRepository.count();
        } catch (DataAccessException e) {
            log.error("Error getting account count", e);
            return -1; // Indicate error
        }
    }

    /**
     * Get service metrics for monitoring
     * @return Map containing operation counts and errors
     */
    public Map<String, Object> getServiceMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();

        // Copy operation counts to avoid concurrent modification
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : operationCounts.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().get());
        }

        // Copy error counts
        Map<String, Integer> errors = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : operationErrors.entrySet()) {
            errors.put(entry.getKey(), entry.getValue().get());
        }

        metrics.put("operationCounts", counts);
        metrics.put("operationErrors", errors);
        metrics.put("accountCount", getAccountCount());

        return metrics;
    }
}