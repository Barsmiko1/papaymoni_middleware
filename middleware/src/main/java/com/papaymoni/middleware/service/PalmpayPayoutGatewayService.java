package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface PalmpayPayoutGatewayService {

    /**
     * Query available bank list from PalmPay
     * @return List of available banks
     */
    List<BankDto> queryBankList();

    /**
     * Query bank account details (name enquiry)
     * @param bankCode Bank code
     * @param accountNumber Account number
     * @return Account details including name
     */
    BankAccountQueryDto queryBankAccount(String bankCode, String accountNumber);

    /**
     * Initiate a payout transaction to bank account
     * @param orderId Unique order ID
     * @param accountName Payee account name
     * @param bankCode Bank code
     * @param accountNumber Account number
     * @param phoneNumber Payee phone number
     * @param amount Amount to transfer
     * @param currency Currency code (e.g., NGN)
     * @param remark Optional description
     * @return Response containing transaction details
     */
    PalmpayPayoutResponseDto initiatePayoutTransaction(
            String orderId,
            String accountName,
            String bankCode,
            String accountNumber,
            String phoneNumber,
            BigDecimal amount,
            String currency,
            String remark);

    /**
     * Query transaction status from PalmPay
     * @param orderId Our order ID
     * @param orderNo PalmPay order number
     * @return Transaction status and details
     */
    PalmpayPayoutResponseDto queryTransactionStatus(String orderId, String orderNo);

    /**
     * Process payout webhook notification
     * @param webhookDto Webhook data
     * @return True if processed successfully
     */
    boolean processPayoutWebhook(PalmpayPayoutWebhookDto webhookDto);
}
