package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.PassimpayCurrenciesResponseDto;
import com.papaymoni.middleware.dto.PassimpayNetworkFeeResponseDto;
import com.papaymoni.middleware.dto.PassimpayWebhookDto;
import com.papaymoni.middleware.dto.PassimpayWithdrawalRequestDto;
import com.papaymoni.middleware.dto.PassimpayWithdrawalStatusResponseDto;
import com.papaymoni.middleware.model.PassimpayWallet;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;

import java.math.BigDecimal;
import java.util.List;


public interface PassimpayService {

    /**
     * Get or create a wallet address for a specific cryptocurrency
     * @param user The user
     * @param currencyCode The cryptocurrency code (e.g., BTC, ETH)
     * @return The wallet address
     */
    PassimpayWallet getOrCreateWalletAddress(User user, String currencyCode);

    /**
     * Get all wallet addresses for a user
     * @param user The user
     * @return List of wallet addresses
     */
    List<PassimpayWallet> getAllWalletAddresses(User user);

    /**
     * Get supported cryptocurrencies
     * @return List of supported cryptocurrencies
     */
    PassimpayCurrenciesResponseDto getSupportedCurrencies();

    /**
     * Process webhook notification for deposit
     * @param webhookDto The webhook data
     * @param signature The signature from headers
     * @return API response
     */
    ApiResponse processDepositWebhook(PassimpayWebhookDto webhookDto, String signature);

    /**
     * Verify webhook signature
     * @param webhookData The webhook data as JSON string
     * @param signature The signature from headers
     * @return True if signature is valid
     */
    boolean verifyWebhookSignature(String webhookData, String signature);

    /**
     * Get network fee for a withdrawal
     * @param paymentId The payment ID (currency/network)
     * @param walletAddress The destination wallet address
     * @param amount The amount to withdraw
     * @return Network fee response
     */


    PassimpayNetworkFeeResponseDto getNetworkFee(Integer paymentId, String walletAddress, BigDecimal amount);

    /**
     * Initiate a withdrawal to a cryptocurrency wallet
     * @param user The user initiating the withdrawal
     * @param withdrawalRequest The withdrawal request details
     * @return API response with transaction details
     */
    ApiResponse<Transaction> initiateWithdrawal(User user, PassimpayWithdrawalRequestDto withdrawalRequest);

    /**
     * Check the status of a withdrawal
     * @param transactionId The Passimpay transaction ID
     * @return Withdrawal status response
     */
    PassimpayWithdrawalStatusResponseDto checkWithdrawalStatus(String transactionId);


    /**
     * Process webhook request and validate signature.
     * @param webhookDto The webhook payload
     * @param signature The signature from headers
     * @return API response
     */
    ApiResponse processWebhook(PassimpayWebhookDto webhookDto, String signature);

    /**
     * Process and update a transaction based on its current status
     * @param transaction The transaction to update
     * @return Updated transaction
     */
    Transaction updateTransactionStatus(Transaction transaction);
}

