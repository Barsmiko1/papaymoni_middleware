package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.WalletBalanceDto;
import com.papaymoni.middleware.dto.WalletTransactionDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.security.CurrentUser;
import com.papaymoni.middleware.service.UserService;
import com.papaymoni.middleware.service.WalletBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletBalanceService walletBalanceService;
    private final UserService userService;

    @GetMapping("/balances")
    public ResponseEntity<ApiResponse> getUserWalletBalances(
            @AuthenticationPrincipal UserDetails currentUser) {
        User user = userService.getUserByUsername(currentUser.getUsername());
        List<WalletBalanceDto> walletBalances = walletBalanceService.getUserWalletBalances(user);

        // Always return a success response, even if the list is empty
        return ResponseEntity.ok(ApiResponse.success("User wallet balances retrieved successfully", walletBalances));
    }

    @GetMapping("/balances/{currencyCode}")
    public ResponseEntity<ApiResponse> getWalletBalanceByCurrency(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String currencyCode) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            WalletBalanceDto walletBalance = walletBalanceService.getWalletBalance(user, currencyCode);
            return ResponseEntity.ok(ApiResponse.success("Wallet balance retrieved successfully", walletBalance));
        } catch (ResourceNotFoundException e) {
            // Return a 404 with a clear message
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Wallet balance not found for currency: " + currencyCode));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse> getUserTransactions(
            @CurrentUser User user,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<WalletTransactionDto> transactions = walletBalanceService.getUserTransactions(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("User transactions retrieved successfully", transactions));
    }

    @GetMapping("/transactions/{currencyCode}")
    public ResponseEntity<ApiResponse> getUserTransactionsByCurrency(
            @CurrentUser User user,
            @PathVariable String currencyCode,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<WalletTransactionDto> transactions = walletBalanceService.getUserTransactionsByCurrency(user, currencyCode, pageable);
        return ResponseEntity.ok(ApiResponse.success("User transactions retrieved successfully", transactions));
    }
}
