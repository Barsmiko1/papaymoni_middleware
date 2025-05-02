package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Order;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentService {
    boolean hasSufficientBalance(User user, BigDecimal amount);
    Transaction processPayment(User user, Order order);
    Transaction processBuyOrderPayment(User user, Order order);
    Transaction processDeposit(VirtualAccount virtualAccount, BigDecimal amount, String reference);
    byte[] getReceiptData(Transaction transaction);
    Transaction findTransaction(String reference);
    BigDecimal calculateFee(BigDecimal amount);

    /**
     * Process a deposit from Palmpay webhook
     * @param virtualAccount the virtual account receiving the deposit
     * @param amount the amount being deposited
     * @param orderReference the Palmpay order reference
     * @param payerDetails details of the payer
     * @return the created transaction
     */
    Transaction processPalmpayDeposit(VirtualAccount virtualAccount, BigDecimal amount, String orderReference, Map<String, String> payerDetails);
}