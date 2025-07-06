package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.WalletBalanceDto;
import com.papaymoni.middleware.dto.WalletTransactionDto;
import com.papaymoni.middleware.model.Currency;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public interface WalletBalanceService {
    WalletBalance getOrCreateWalletBalance(User user, Currency currency);
    WalletBalanceDto getWalletBalance(User user, String currencyCode);
    List<WalletBalanceDto> getUserWalletBalances(User user);

    // Original methods for backward compatibility
    @Transactional
    WalletBalance updateWalletBalance(User user, String currency, BigDecimal amount);

    @Transactional
    WalletBalance creditWallet(User user, String currency, BigDecimal amount);

    @Transactional
    WalletBalance debitWallet(User user, String currency, BigDecimal amount);

    @Transactional
    WalletBalance freezeAmount(User user, String currency, BigDecimal amount);

    @Transactional
    WalletBalance unfreezeAmount(User user, String currency, BigDecimal amount);

    // Enhanced methods with additional parameters
    @Transactional
    WalletTransactionDto creditWallet(User user, String currencyCode, BigDecimal amount,
                                      String description, String referenceId, String referenceType,
                                      String performedBy, String ipAddress);

    @Transactional
    WalletTransactionDto debitWallet(User user, String currencyCode, BigDecimal amount,
                                     String description, String referenceId, String referenceType,
                                     String performedBy, String ipAddress);

    @Transactional
    WalletTransactionDto freezeAmount(User user, String currencyCode, BigDecimal amount,
                                      String description, String referenceId, String referenceType,
                                      String performedBy, String ipAddress);

    @Transactional
    WalletTransactionDto unfreezeAmount(User user, String currencyCode, BigDecimal amount,
                                        String description, String referenceId, String referenceType,
                                        String performedBy, String ipAddress);

    BigDecimal getAvailableBalance(User user, String currencyCode);
    boolean hasSufficientBalance(User user, String currencyCode, BigDecimal amount);

    Page<WalletTransactionDto> getUserTransactions(User user, Pageable pageable);
    Page<WalletTransactionDto> getUserTransactionsByCurrency(User user, String currencyCode, Pageable pageable);

    void initializeUserWallets(User user);
}
