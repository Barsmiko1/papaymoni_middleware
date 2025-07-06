package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.BankAccountQueryDto;
import com.papaymoni.middleware.dto.BankDto;
import com.papaymoni.middleware.dto.WithdrawalRequestDto;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;

import java.util.List;
import java.util.Map;

public interface NgnPalmpayWithdrawalService {

    /**
     * Get list of available banks
     * @return List of banks
     */
    List<BankDto> getAvailableBanks();

    /**
     * Perform name enquiry for bank account
     * @param bankCode Bank code
     * @param accountNumber Account number
     * @return Account details
     */
    BankAccountQueryDto performNameEnquiry(String bankCode, String accountNumber);

    /**
     * Initiate withdrawal request
     * @param user User requesting withdrawal
     * @param withdrawalRequest Withdrawal request details
     * @return Transaction details
     */
    ApiResponse<Transaction> initiateWithdrawal(User user, WithdrawalRequestDto withdrawalRequest);

    /**
     * Complete a withdrawal transaction
     * @param transaction Transaction to complete
     * @param payoutResponse Response from payment gateway
     * @return Updated transaction
     */
    Transaction completeWithdrawal(Transaction transaction, Map<String, Object> payoutResponse);

    /**
     * Handle failed withdrawal
     * @param transaction Transaction that failed
     * @param errorReason Reason for failure
     * @return Updated transaction
     */
    Transaction handleFailedWithdrawal(Transaction transaction, String errorReason);

    /**
     * Check transaction status and update if needed
     * @param transactionId Transaction ID
     * @return Updated transaction
     */
    Transaction checkAndUpdateTransactionStatus(Long transactionId);
}
