package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;

import java.util.List;

public interface VirtualAccountService {
    List<VirtualAccount> getUserVirtualAccounts(User user);
    VirtualAccount createVirtualAccount(User user, String currency);
    VirtualAccount getVirtualAccountById(Long id);
    VirtualAccount getVirtualAccountByAccountNumber(String accountNumber);
    List<VirtualAccount> getUserVirtualAccountsByCurrency(User user, String currency);
}
