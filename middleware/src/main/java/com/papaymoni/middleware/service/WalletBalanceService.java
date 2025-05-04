package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.WalletBalance;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

public interface WalletBalanceService {
    WalletBalance getOrCreateWalletBalance(User user, String currency);
    WalletBalance updateWalletBalance(User user, String currency, BigDecimal amount);
    BigDecimal getAvailableBalance(User user, String currency);
    boolean hasSufficientBalance(User user, String currency, BigDecimal amount);
    List<WalletBalance> getUserWalletBalances(User user);
    WalletBalance freezeAmount(User user, String currency, BigDecimal amount);
    WalletBalance unfreezeAmount(User user, String currency, BigDecimal amount);
    WalletBalance debitWallet(User user, String currency, BigDecimal amount);
    WalletBalance creditWallet(User user, String currency, BigDecimal amount);
    void initializeUserWallets(User user);

}