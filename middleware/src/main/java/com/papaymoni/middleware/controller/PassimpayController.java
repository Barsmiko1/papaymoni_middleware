package com.papaymoni.middleware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.papaymoni.middleware.dto.*;
import com.papaymoni.middleware.model.PassimpayWallet;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.PassimpayService;
import com.papaymoni.middleware.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import com.papaymoni.middleware.repository.TransactionRepository;

@Slf4j
@RestController
@RequestMapping("/api/passimpay")
@RequiredArgsConstructor
public class PassimpayController {

    private final PassimpayService passimpayService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;

    @GetMapping("/wallets")
    public ResponseEntity<ApiResponse<List<PassimpayWallet>>> getUserWallets(
            @AuthenticationPrincipal UserDetails currentUser) {
        User user = userService.getUserByUsername(currentUser.getUsername());
        List<PassimpayWallet> wallets = passimpayService.getAllWalletAddresses(user);
        return ResponseEntity.ok(ApiResponse.success("Wallets retrieved successfully", wallets));
    }

    @PostMapping("/wallets/{currency}")
    public ResponseEntity<ApiResponse<PassimpayWallet>> createWallet(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String currency) {
        User user = userService.getUserByUsername(currentUser.getUsername());
        PassimpayWallet wallet = passimpayService.getOrCreateWalletAddress(user, currency);
        return ResponseEntity.ok(ApiResponse.success("Wallet created successfully", wallet));
    }

    @GetMapping("/currencies")
    public ResponseEntity<ApiResponse<PassimpayCurrenciesResponseDto>> getSupportedCurrencies() {
        PassimpayCurrenciesResponseDto currencies = passimpayService.getSupportedCurrencies();
        return ResponseEntity.ok(ApiResponse.success("Currencies retrieved successfully", currencies));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse> handleWebhook(
            @RequestBody PassimpayWebhookDto webhookDto,
            @RequestHeader("x-signature") String signature
    ) {
        ApiResponse response = passimpayService.processWebhook(webhookDto, signature);
        return ResponseEntity.ok(response);
                //new ApiResponse<>(response.isSuccess(), response.getMessage(), (String) response.getData());
    }


    @GetMapping("/network-fee")
    public ResponseEntity<ApiResponse<PassimpayNetworkFeeResponseDto>> getNetworkFee(
            @RequestParam Integer paymentId,
            @RequestParam String walletAddress,
            @RequestParam BigDecimal amount) {
        try {
            PassimpayNetworkFeeResponseDto feeResponse =
                    passimpayService.getNetworkFee(paymentId, walletAddress, amount);
            return ResponseEntity.ok(ApiResponse.success("Network fee retrieved successfully", feeResponse));
        } catch (Exception e) {
            log.error("Error getting network fee", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get network fee: " + e.getMessage()));
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<Transaction>> initiateWithdrawal(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody PassimpayWithdrawalRequestDto withdrawalRequest) {
        User user = userService.getUserByUsername(currentUser.getUsername());
        ApiResponse<Transaction> response = passimpayService.initiateWithdrawal(user, withdrawalRequest);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/withdraw/status/{transactionId}")
    public ResponseEntity<ApiResponse<PassimpayWithdrawalStatusResponseDto>> checkWithdrawalStatus(
            @PathVariable String transactionId) {
        try {
            PassimpayWithdrawalStatusResponseDto statusResponse =
                    passimpayService.checkWithdrawalStatus(transactionId);
            return ResponseEntity.ok(ApiResponse.success("Withdrawal status retrieved successfully", statusResponse));
        } catch (Exception e) {
            log.error("Error checking withdrawal status", e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to check withdrawal status: " + e.getMessage()));
        }
    }

    @GetMapping("/transaction/{id}/update-status")
    public ResponseEntity<ApiResponse<Transaction>> updateTransactionStatus(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long id) {
        User user = userService.getUserByUsername(currentUser.getUsername());

        // Find the transaction by ID
        Transaction transaction = transactionRepository.findById(id).orElse(null);

        // Check if transaction exists and belongs to the user
        if (transaction == null || !transaction.getUser().getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Transaction not found or not authorized"));
        }

        Transaction updatedTransaction = passimpayService.updateTransactionStatus(transaction);
        return ResponseEntity.ok(ApiResponse.success("Transaction status updated", updatedTransaction));
    }
}
