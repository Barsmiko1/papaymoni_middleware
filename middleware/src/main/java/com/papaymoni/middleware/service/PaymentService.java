package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentService {
    boolean hasSufficientBalance(User user, BigDecimal amount);
    boolean hasSufficientBalance(User user, BigDecimal amount, String currency);  // Add this new method
    Transaction processPayment(User user, Order order);
    Transaction processBuyOrderPayment(User user, Order order);
    Transaction processDeposit(VirtualAccount virtualAccount, BigDecimal amount, String reference);
    byte[] getReceiptData(Transaction transaction);
    Transaction findTransaction(String reference);
    BigDecimal calculateFee(BigDecimal amount);
    BigDecimal calculateFee(BigDecimal amount, String currency);  // Also add currency support for fee calculation
    Transaction processPalmpayDeposit(VirtualAccount virtualAccount, BigDecimal amount, String orderReference, Map<String, String> payerDetails);
}