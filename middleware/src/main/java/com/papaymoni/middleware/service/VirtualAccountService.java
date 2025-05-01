package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface VirtualAccountService {
    List<VirtualAccount> getUserVirtualAccounts(User user);
    VirtualAccount createVirtualAccount(User user, String currency);
    VirtualAccount getVirtualAccountById(Long id);
    VirtualAccount getVirtualAccountByAccountNumber(String accountNumber);
    List<VirtualAccount> getUserVirtualAccountsByCurrency(User user, String currency);

    /**
     * Create a virtual account asynchronously
     * @param user User to create account for
     * @param currency Currency of the account
     * @return CompletableFuture containing the created virtual account
     */
    CompletableFuture<VirtualAccount> createVirtualAccountAsync(User user, String currency);

    /**
     * Update account balance
     * @param account Account to update
     * @param newBalance New account balance
     * @return Updated account
     */
    VirtualAccount updateAccountBalance(VirtualAccount account, BigDecimal newBalance);

    /**
     * Update multiple account balances in a single transaction
     * @param accountUpdates Map of account IDs to new balances
     */
    void updateAccountBalances(Map<Long, BigDecimal> accountUpdates);

    /**
     * Get the total number of virtual accounts (for health checks)
     * @return Count of accounts
     */
    default long getAccountCount() {
        // This would be implemented in the concrete class
        return 0;
    }
}