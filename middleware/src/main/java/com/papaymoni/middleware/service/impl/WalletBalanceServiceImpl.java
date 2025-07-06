package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.CurrencyDto;
import com.papaymoni.middleware.dto.WalletBalanceDto;
import com.papaymoni.middleware.dto.WalletTransactionDto;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import com.papaymoni.middleware.model.WalletTransaction;
import com.papaymoni.middleware.model.WalletTransaction.TransactionType;
import com.papaymoni.middleware.repository.CurrencyRepository;
import com.papaymoni.middleware.repository.WalletBalanceRepository;
import com.papaymoni.middleware.repository.WalletTransactionRepository;
import com.papaymoni.middleware.service.CurrencyService;
import com.papaymoni.middleware.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletBalanceServiceImpl implements WalletBalanceService {

    private final WalletBalanceRepository walletBalanceRepository;
    private final CurrencyRepository currencyRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CurrencyService currencyService;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance updateWalletBalance(User user, String currencyCode, BigDecimal amount) {
        // Check for null user
        if (user == null) {
            log.error("User is null in updateWalletBalance");
            throw new IllegalArgumentException("User cannot be null");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);
        WalletBalance walletBalance = getOrCreateWalletBalance(user, currency);

        BigDecimal newAvailableBalance = walletBalance.getAvailableBalance().add(amount);
        if (newAvailableBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in " + currencyCode + " wallet");
        }

        walletBalance.setAvailableBalance(newAvailableBalance);
        walletBalance.setTotalBalance(newAvailableBalance.add(walletBalance.getFrozenBalance()));
        walletBalance.setUpdatedAt(LocalDateTime.now());
        walletBalance.setUpdatedBy("SYSTEM");

        log.info("Updated wallet balance for user {} currency {}: available={}, total={}",
                user.getId(), currencyCode, walletBalance.getAvailableBalance(), walletBalance.getTotalBalance());

        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletBalanceDto> getUserWalletBalances(User user) {
        // Check for null user
        if (user == null) {
            log.error("User is null in getUserWalletBalances");
            throw new IllegalArgumentException("User cannot be null");
        }

        log.debug("Getting wallet balances for user: {}", user.getUsername());
        List<WalletBalance> balances;

        try {
            balances = walletBalanceRepository.findByUser(user);
        } catch (Exception e) {
            log.error("Error getting wallet balances for user {}", user.getId(), e);
            throw e;
        }

        // Check if any balances have invalid currency references
        List<WalletBalance> validBalances = balances.stream()
                .filter(balance -> {
                    try {
                        // This will trigger the entity load
                        Currency currency = balance.getCurrency();
                        return currency != null && currency.getId() != null;
                    } catch (EntityNotFoundException e) {
                        log.error("Invalid currency reference in wallet balance ID: {}", balance.getId());
                        return false;
                    }
                })
                .collect(Collectors.toList());

        return validBalances.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance creditWallet(User user, String currencyCode, BigDecimal amount) {
        // Check for null user
        if (user == null) {
            log.error("User is null in creditWallet");
            throw new IllegalArgumentException("User cannot be null");
        }

        // Call the enhanced method with default values
        creditWallet(user, currencyCode, amount,
                "System credit", null, "SYSTEM_CREDIT",
                "SYSTEM", "0.0.0.0");

        // Return the updated wallet balance for backward compatibility
        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        WalletBalance balance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found"));

        return balance;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance debitWallet(User user, String currencyCode, BigDecimal amount) {
        // Check for null user
        if (user == null) {
            log.error("User is null in debitWallet");
            throw new IllegalArgumentException("User cannot be null");
        }

        // Call the enhanced method with default values
        debitWallet(user, currencyCode, amount,
                "System debit", null, "SYSTEM_DEBIT",
                "SYSTEM", "0.0.0.0");

        // Return the updated wallet balance for backward compatibility
        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        WalletBalance balance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found"));

        return balance;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance freezeAmount(User user, String currencyCode, BigDecimal amount) {
        // Check for null user
        if (user == null) {
            log.error("User is null in freezeAmount");
            throw new IllegalArgumentException("User cannot be null");
        }

        // Call the enhanced method with default values
        freezeAmount(user, currencyCode, amount,
                "System freeze", null, "SYSTEM_FREEZE",
                "SYSTEM", "0.0.0.0");

        // Return the updated wallet balance for backward compatibility
        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        WalletBalance balance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found"));

        return balance;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance unfreezeAmount(User user, String currencyCode, BigDecimal amount) {
        // Check for null user
        if (user == null) {
            log.error("User is null in unfreezeAmount");
            throw new IllegalArgumentException("User cannot be null");
        }

        // Call the enhanced method with default values
        unfreezeAmount(user, currencyCode, amount,
                "System unfreeze", null, "SYSTEM_UNFREEZE",
                "SYSTEM", "0.0.0.0");

        // Return the updated wallet balance for backward compatibility
        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        WalletBalance balance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found"));

        return balance;
    }

    // Enhanced methods with additional parameters
    @Override
    @Transactional
    public WalletBalance getOrCreateWalletBalance(User user, Currency currency) {
        // Check for null user
        if (user == null) {
            log.error("User is null in getOrCreateWalletBalance");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (currency == null) {
            log.error("Currency is null in getOrCreateWalletBalance");
            throw new IllegalArgumentException("Currency cannot be null");
        }

        log.debug("Getting or creating wallet balance for user ID: {}, currency: {}",
                user.getId(), currency.getCode());

        try {
            Optional<WalletBalance> existingBalance = walletBalanceRepository.findByUserAndCurrency(user, currency);

            if (existingBalance.isPresent()) {
                return existingBalance.get();
            } else {
                return createNewWalletBalance(user, currency);
            }
        } catch (Exception e) {
            log.error("Error getting or creating wallet balance", e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WalletBalanceDto getWalletBalance(User user, String currencyCode) {
        // Check for null user
        if (user == null) {
            log.error("User is null in getWalletBalance");
            throw new IllegalArgumentException("User cannot be null");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        WalletBalance walletBalance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));

        return convertToDto(walletBalance);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletTransactionDto creditWallet(User user, String currencyCode, BigDecimal amount,
                                             String description, String referenceId, String referenceType,
                                             String performedBy, String ipAddress) {
        // Check for null user
        if (user == null) {
            log.error("User is null in creditWallet (enhanced)");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        // Use lock for concurrent operations
        WalletBalance walletBalance;
        try {
            Optional<WalletBalance> existingBalance = walletBalanceRepository.findByUserAndCurrencyWithLock(user, currency);
            walletBalance = existingBalance.orElseGet(() -> createNewWalletBalance(user, currency));
        } catch (Exception e) {
            log.error("Error getting wallet balance with lock", e);
            // Fallback to non-locked version
            walletBalance = getOrCreateWalletBalance(user, currency);
        }

        BigDecimal balanceBefore = walletBalance.getAvailableBalance();
        walletBalance.setAvailableBalance(balanceBefore.add(amount));
        walletBalance.setTotalBalance(walletBalance.getAvailableBalance().add(walletBalance.getFrozenBalance()));
        walletBalance.setUpdatedBy(performedBy);
        walletBalance.setUpdatedAt(LocalDateTime.now());

        WalletBalance updatedBalance = walletBalanceRepository.save(walletBalance);
        log.info("Credited {} {} to user {} wallet", amount, currencyCode, user.getId());

        // Record the transaction
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setCurrency(currency);
        transaction.setType(TransactionType.CREDIT);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(updatedBalance.getAvailableBalance());
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceType(referenceType);
        transaction.setCreatedBy(performedBy);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIpAddress(ipAddress);

        WalletTransaction savedTransaction = walletTransactionRepository.save(transaction);
        return convertToTransactionDto(savedTransaction);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletTransactionDto debitWallet(User user, String currencyCode, BigDecimal amount,
                                            String description, String referenceId, String referenceType,
                                            String performedBy, String ipAddress) {
        // Check for null user
        if (user == null) {
            log.error("User is null in debitWallet (enhanced)");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        // Use lock for concurrent operations
        WalletBalance walletBalance;
        try {
            walletBalance = walletBalanceRepository.findByUserAndCurrencyWithLock(user, currency)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));
        } catch (Exception e) {
            log.error("Error getting wallet balance with lock", e);
            // Fallback to non-locked version
            walletBalance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));
        }

        if (walletBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for debit");
        }

        BigDecimal balanceBefore = walletBalance.getAvailableBalance();
        walletBalance.setAvailableBalance(balanceBefore.subtract(amount));
        walletBalance.setTotalBalance(walletBalance.getAvailableBalance().add(walletBalance.getFrozenBalance()));
        walletBalance.setUpdatedBy(performedBy);
        walletBalance.setUpdatedAt(LocalDateTime.now());

        WalletBalance updatedBalance = walletBalanceRepository.save(walletBalance);
        log.info("Debited {} {} from user {} wallet", amount, currencyCode, user.getId());

        // Record the transaction
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setCurrency(currency);
        transaction.setType(TransactionType.DEBIT);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(updatedBalance.getAvailableBalance());
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceType(referenceType);
        transaction.setCreatedBy(performedBy);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIpAddress(ipAddress);

        WalletTransaction savedTransaction = walletTransactionRepository.save(transaction);
        return convertToTransactionDto(savedTransaction);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletTransactionDto freezeAmount(User user, String currencyCode, BigDecimal amount,
                                             String description, String referenceId, String referenceType,
                                             String performedBy, String ipAddress) {
        // Check for null user
        if (user == null) {
            log.error("User is null in freezeAmount (enhanced)");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Freeze amount must be positive");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        // Use lock for concurrent operations
        WalletBalance walletBalance;
        try {
            walletBalance = walletBalanceRepository.findByUserAndCurrencyWithLock(user, currency)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));
        } catch (Exception e) {
            log.error("Error getting wallet balance with lock", e);
            // Fallback to non-locked version
            walletBalance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));
        }

        if (walletBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient available balance to freeze");
        }

        BigDecimal balanceBefore = walletBalance.getAvailableBalance();
        walletBalance.setAvailableBalance(balanceBefore.subtract(amount));
        walletBalance.setFrozenBalance(walletBalance.getFrozenBalance().add(amount));
        walletBalance.setUpdatedBy(performedBy);
        walletBalance.setUpdatedAt(LocalDateTime.now());

        WalletBalance updatedBalance = walletBalanceRepository.save(walletBalance);
        log.info("Froze {} {} for user {}", amount, currencyCode, user.getId());

        // Record the transaction
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setCurrency(currency);
        transaction.setType(TransactionType.FREEZE);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(updatedBalance.getAvailableBalance());
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceType(referenceType);
        transaction.setCreatedBy(performedBy);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIpAddress(ipAddress);

        WalletTransaction savedTransaction = walletTransactionRepository.save(transaction);
        return convertToTransactionDto(savedTransaction);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletTransactionDto unfreezeAmount(User user, String currencyCode, BigDecimal amount,
                                               String description, String referenceId, String referenceType,
                                               String performedBy, String ipAddress) {
        // Check for null user
        if (user == null) {
            log.error("User is null in unfreezeAmount (enhanced)");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unfreeze amount must be positive");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        // Use lock for concurrent operations
        WalletBalance walletBalance;
        try {
            walletBalance = walletBalanceRepository.findByUserAndCurrencyWithLock(user, currency)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));
        } catch (Exception e) {
            log.error("Error getting wallet balance with lock", e);
            // Fallback to non-locked version
            walletBalance = walletBalanceRepository.findByUserAndCurrency(user, currency)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found for currency: " + currencyCode));
        }

        if (walletBalance.getFrozenBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient frozen balance to unfreeze");
        }

        BigDecimal frozenBefore = walletBalance.getFrozenBalance();
        walletBalance.setFrozenBalance(frozenBefore.subtract(amount));
        walletBalance.setAvailableBalance(walletBalance.getAvailableBalance().add(amount));
        walletBalance.setUpdatedBy(performedBy);
        walletBalance.setUpdatedAt(LocalDateTime.now());

        WalletBalance updatedBalance = walletBalanceRepository.save(walletBalance);
        log.info("Unfroze {} {} for user {}", amount, currencyCode, user.getId());

        // Record the transaction
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setCurrency(currency);
        transaction.setType(TransactionType.UNFREEZE);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(frozenBefore);
        transaction.setBalanceAfter(updatedBalance.getFrozenBalance());
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceType(referenceType);
        transaction.setCreatedBy(performedBy);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIpAddress(ipAddress);

        WalletTransaction savedTransaction = walletTransactionRepository.save(transaction);
        return convertToTransactionDto(savedTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(User user, String currencyCode) {
        // Check for null user
        if (user == null) {
            log.error("User is null in getAvailableBalance");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (currencyCode == null || currencyCode.isEmpty()) {
            log.error("Currency code is null or empty in getAvailableBalance");
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }

        try {
            // Get currency entity
            Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);
            if (currency == null) {
                log.error("Currency not found for code: {}", currencyCode);
                return BigDecimal.ZERO;
            }

            // Get wallet balance
            Optional<WalletBalance> walletBalance = walletBalanceRepository.findByUserAndCurrency(user, currency);

            if (!walletBalance.isPresent()) {
                log.info("No wallet balance found for user ID: {}, currency: {}. Creating new wallet.",
                        user.getId(), currencyCode);
                // Create wallet balance if it doesn't exist
                WalletBalance newBalance = createNewWalletBalance(user, currency);
                return newBalance.getAvailableBalance();
            }

            return walletBalance.get().getAvailableBalance();

        } catch (Exception e) {
            log.error("Error getting available balance for user ID: {}, currency: {}",
                    user.getId(), currencyCode, e);
            // Return zero in case of error to prevent further issues
            return BigDecimal.ZERO;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(User user, String currencyCode, BigDecimal amount) {
        // Check for null user
        if (user == null) {
            log.error("User is null in hasSufficientBalance");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        BigDecimal availableBalance = getAvailableBalance(user, currencyCode);
        log.debug("Checking if user {} has sufficient balance: required={}, available={}",
                user.getUsername(), amount, availableBalance);
        return availableBalance.compareTo(amount) >= 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionDto> getUserTransactions(User user, Pageable pageable) {
        // Check for null user
        if (user == null) {
            log.error("User is null in getUserTransactions");
            throw new IllegalArgumentException("User cannot be null");
        }

        return walletTransactionRepository.findByUser(user, pageable)
                .map(this::convertToTransactionDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionDto> getUserTransactionsByCurrency(User user, String currencyCode, Pageable pageable) {
        // Check for null user
        if (user == null) {
            log.error("User is null in getUserTransactionsByCurrency");
            throw new IllegalArgumentException("User cannot be null");
        }

        Currency currency = currencyService.getCurrencyEntityByCode(currencyCode);

        return walletTransactionRepository.findByUserAndCurrency(user, currency, pageable)
                .map(this::convertToTransactionDto);
    }

    @Override
    @Transactional
    public void initializeUserWallets(User user) {
        // Check for null user
        if (user == null) {
            log.error("User is null in initializeUserWallets");
            throw new IllegalArgumentException("User cannot be null");
        }

        List<Currency> activeCurrencies = currencyService.getActiveCurrencies().stream()
                .map(dto -> {
                    Currency currency = new Currency();
                    currency.setId(dto.getId());
                    return currency;
                })
                .collect(Collectors.toList());

        for (Currency currency : activeCurrencies) {
            getOrCreateWalletBalance(user, currency);
        }
        log.info("Initialized wallet balances for user {} with active currencies", user.getId());
    }

    private WalletBalance createNewWalletBalance(User user, Currency currency) {
        // Check for null user
        if (user == null) {
            log.error("User is null in createNewWalletBalance");
            throw new IllegalArgumentException("User cannot be null");
        }

        if (currency == null) {
            log.error("Currency is null in createNewWalletBalance");
            throw new IllegalArgumentException("Currency cannot be null");
        }

        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setUser(user);
        walletBalance.setCurrency(currency);
        walletBalance.setAvailableBalance(BigDecimal.ZERO);
        walletBalance.setFrozenBalance(BigDecimal.ZERO);
        walletBalance.setTotalBalance(BigDecimal.ZERO);
        walletBalance.setCreatedBy("SYSTEM");
        walletBalance.setUpdatedBy("SYSTEM");
        walletBalance.setCreatedAt(LocalDateTime.now());
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Creating new wallet balance for user {} with currency {}", user.getId(), currency.getCode());
        return walletBalanceRepository.save(walletBalance);
    }

    private WalletBalanceDto convertToDto(WalletBalance walletBalance) {
        if (walletBalance == null) {
            return null;
        }

        WalletBalanceDto dto = new WalletBalanceDto();
        dto.setId(walletBalance.getId());
        dto.setUserId(walletBalance.getUser().getId());
        dto.setUsername(walletBalance.getUser().getUsername());

        CurrencyDto currencyDto = new CurrencyDto();
        currencyDto.setId(walletBalance.getCurrency().getId());
        currencyDto.setCode(walletBalance.getCurrency().getCode());
        currencyDto.setName(walletBalance.getCurrency().getName());
        currencyDto.setSymbol(walletBalance.getCurrency().getSymbol());
        currencyDto.setDecimalPlaces(walletBalance.getCurrency().getDecimalPlaces());
        currencyDto.setActive(walletBalance.getCurrency().getActive());

        dto.setCurrency(currencyDto);
        dto.setAvailableBalance(walletBalance.getAvailableBalance());
        dto.setFrozenBalance(walletBalance.getFrozenBalance());
        dto.setTotalBalance(walletBalance.getTotalBalance());
        dto.setUpdatedAt(walletBalance.getUpdatedAt());

        return dto;
    }

    private WalletTransactionDto convertToTransactionDto(WalletTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        WalletTransactionDto dto = new WalletTransactionDto();
        dto.setId(transaction.getId());
        dto.setUserId(transaction.getUser().getId());
        dto.setUsername(transaction.getUser().getUsername());

        CurrencyDto currencyDto = new CurrencyDto();
        currencyDto.setId(transaction.getCurrency().getId());
        currencyDto.setCode(transaction.getCurrency().getCode());
        currencyDto.setName(transaction.getCurrency().getName());
        currencyDto.setSymbol(transaction.getCurrency().getSymbol());
        currencyDto.setDecimalPlaces(transaction.getCurrency().getDecimalPlaces());

        dto.setCurrency(currencyDto);
        dto.setType(transaction.getType());
        dto.setAmount(transaction.getAmount());
        dto.setBalanceBefore(transaction.getBalanceBefore());
        dto.setBalanceAfter(transaction.getBalanceAfter());
        dto.setDescription(transaction.getDescription());
        dto.setReferenceId(transaction.getReferenceId());
        dto.setReferenceType(transaction.getReferenceType());
        dto.setCreatedAt(transaction.getCreatedAt());
        dto.setCreatedBy(transaction.getCreatedBy());

        return dto;
    }
}