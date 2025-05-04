package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import com.papaymoni.middleware.repository.WalletBalanceRepository;
import com.papaymoni.middleware.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletBalanceServiceImpl implements WalletBalanceService {

    private final WalletBalanceRepository walletBalanceRepository;

    @Override
    @Transactional
    public WalletBalance getOrCreateWalletBalance(User user, String currency) {
        return walletBalanceRepository.findByUserAndCurrency(user, currency)
                .orElseGet(() -> createNewWalletBalance(user, currency));
    }

    private WalletBalance createNewWalletBalance(User user, String currency) {
        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setUser(user);
        walletBalance.setCurrency(currency);
        walletBalance.setAvailableBalance(BigDecimal.ZERO);
        walletBalance.setFrozenBalance(BigDecimal.ZERO);
        walletBalance.setTotalBalance(BigDecimal.ZERO);
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Creating new wallet balance for user {} with currency {}", user.getId(), currency);
        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance updateWalletBalance(User user, String currency, BigDecimal amount) {
        WalletBalance walletBalance = getOrCreateWalletBalance(user, currency);

        BigDecimal newAvailableBalance = walletBalance.getAvailableBalance().add(amount);
        if (newAvailableBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in " + currency + " wallet");
        }

        walletBalance.setAvailableBalance(newAvailableBalance);
        walletBalance.setTotalBalance(newAvailableBalance.add(walletBalance.getFrozenBalance()));
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Updated wallet balance for user {} currency {}: available={}, total={}",
                user.getId(), currency, walletBalance.getAvailableBalance(), walletBalance.getTotalBalance());

        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(User user, String currency) {
        return walletBalanceRepository.findByUserAndCurrency(user, currency)
                .map(WalletBalance::getAvailableBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(User user, String currency, BigDecimal amount) {
        BigDecimal availableBalance = getAvailableBalance(user, currency);
        return availableBalance.compareTo(amount) >= 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletBalance> getUserWalletBalances(User user) {
        return walletBalanceRepository.findByUser(user);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance freezeAmount(User user, String currency, BigDecimal amount) {
        WalletBalance walletBalance = getOrCreateWalletBalance(user, currency);

        if (walletBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient available balance to freeze");
        }

        walletBalance.setAvailableBalance(walletBalance.getAvailableBalance().subtract(amount));
        walletBalance.setFrozenBalance(walletBalance.getFrozenBalance().add(amount));
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Froze {} {} for user {}", amount, currency, user.getId());
        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance unfreezeAmount(User user, String currency, BigDecimal amount) {
        WalletBalance walletBalance = getOrCreateWalletBalance(user, currency);

        if (walletBalance.getFrozenBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient frozen balance to unfreeze");
        }

        walletBalance.setFrozenBalance(walletBalance.getFrozenBalance().subtract(amount));
        walletBalance.setAvailableBalance(walletBalance.getAvailableBalance().add(amount));
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Unfroze {} {} for user {}", amount, currency, user.getId());
        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance debitWallet(User user, String currency, BigDecimal amount) {
        WalletBalance walletBalance = getOrCreateWalletBalance(user, currency);

        if (walletBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for debit");
        }

        walletBalance.setAvailableBalance(walletBalance.getAvailableBalance().subtract(amount));
        walletBalance.setTotalBalance(walletBalance.getTotalBalance().subtract(amount));
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Debited {} {} from user {} wallet", amount, currency, user.getId());
        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletBalance creditWallet(User user, String currency, BigDecimal amount) {
        WalletBalance walletBalance = getOrCreateWalletBalance(user, currency);

        walletBalance.setAvailableBalance(walletBalance.getAvailableBalance().add(amount));
        walletBalance.setTotalBalance(walletBalance.getTotalBalance().add(amount));
        walletBalance.setUpdatedAt(LocalDateTime.now());

        log.info("Credited {} {} to user {} wallet", amount, currency, user.getId());
        return walletBalanceRepository.save(walletBalance);
    }

    @Override
    @Transactional
    public void initializeUserWallets(User user) {
        List<String> currencies = Arrays.asList("NGN", "EUR", "USDT", "USD", "GBP");
        for (String currency : currencies) {
            getOrCreateWalletBalance(user, currency);
        }
        log.info("Initialized wallet balances for user {} with currencies: {}", user.getId(), currencies);
    }
}