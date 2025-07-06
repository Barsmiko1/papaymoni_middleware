package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.BankAccountQueryDto;
import com.papaymoni.middleware.dto.BankDto;
import com.papaymoni.middleware.dto.WithdrawalRequestDto;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.UserService;
import com.papaymoni.middleware.service.NgnPalmpayWithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final NgnPalmpayWithdrawalService ngnPalmpayWithdrawalService;
    private final UserService userService;

    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<BankDto>>> getAvailableBanks() {
        log.info("Getting available banks");

        List<BankDto> banks = ngnPalmpayWithdrawalService.getAvailableBanks();

        return ResponseEntity.ok(
                ApiResponse.success("Banks retrieved successfully", banks)
        );
    }

    @GetMapping("/name-enquiry")
    public ResponseEntity<ApiResponse<BankAccountQueryDto>> nameEnquiry(
            @RequestParam("bankCode") String bankCode,
            @RequestParam("accountNumber") String accountNumber) {
        log.info("Performing name enquiry for bank: {}, account: {}", bankCode, accountNumber);

        BankAccountQueryDto accountDetails = ngnPalmpayWithdrawalService.performNameEnquiry(bankCode, accountNumber);

        if ("Success".equals(accountDetails.getStatus())) {
            return ResponseEntity.ok(
                    ApiResponse.success("Account details retrieved successfully", accountDetails)
            );
        } else {
            return ResponseEntity.ok(
                    ApiResponse.error("Failed to retrieve account details: " + accountDetails.getStatus())
            );
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Transaction>> initiateWithdrawal(
            @Valid @RequestBody WithdrawalRequestDto withdrawalRequest,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Initiating withdrawal: {}", withdrawalRequest);

        User user = userService.getUserByUsername(currentUser.getUsername());
        log.info("Before Withdrawal: {}", user.toString());

        ApiResponse<Transaction> response = ngnPalmpayWithdrawalService.initiateWithdrawal(user, withdrawalRequest);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Transaction>> checkWithdrawalStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Checking status of withdrawal: {}", id);

        User user = userService.getUserByUsername(currentUser.getUsername());
        Transaction transaction = ngnPalmpayWithdrawalService.checkAndUpdateTransactionStatus(id);

        // Ensure the transaction belongs to the requesting user
        if (!transaction.getUser().getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Transaction not found or not authorized")
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success("Withdrawal status retrieved successfully", transaction)
        );
    }
}
