package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.ReceiptService;
import com.papaymoni.middleware.service.TransactionService;
import com.papaymoni.middleware.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserService userService;
    private final ReceiptService receiptService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Transaction>>> getUserTransactions(
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            List<Transaction> transactions = transactionService.getUserTransactions(user);
            log.info("Transactions");
            log.info("User Transactions: {}", transactions.size());
            log.info("User Transactions: {}", transactions);


            return ResponseEntity.ok(
                    ApiResponse.success("Transactions retrieved successfully", transactions)
            );
        } catch (Exception e) {
            log.error("Error retrieving transactions: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transactions: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Transaction>> getTransactionById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            Transaction transaction = transactionService.getTransactionById(id);

            // Verify that the transaction belongs to the user
            if (!transaction.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You don't have permission to view this transaction"));
            }

            return ResponseEntity.ok(
                    ApiResponse.success("Transaction retrieved successfully", transaction)
            );
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving transaction: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transaction: " + e.getMessage()));
        }
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactionsByType(
            @PathVariable String type,
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            List<Transaction> transactions = transactionService.getUserTransactionsByType(user, type);

            return ResponseEntity.ok(
                    ApiResponse.success("Transactions retrieved successfully", transactions)
            );
        } catch (Exception e) {
            log.error("Error retrieving transactions by type: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transactions: " + e.getMessage()));
        }
    }

    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactionsByDateRange(
            @RequestParam("start") String startDate,
            @RequestParam("end") String endDate,
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);

            List<Transaction> transactions = transactionService.getUserTransactionsByDateRange(user, start, end);

            return ResponseEntity.ok(
                    ApiResponse.success("Transactions retrieved successfully", transactions)
            );
        } catch (Exception e) {
            log.error("Error retrieving transactions by date range: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transactions: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/receipt")
    @Cacheable(value = "transactionReceipts", key = "#id")
    public ResponseEntity<?> getTransactionReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            Transaction transaction = transactionService.getTransactionById(id);

            // Verify that the transaction belongs to the user
            if (!transaction.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You don't have permission to view this receipt"));
            }

            // Check if receipt exists
            if (transaction.getReceiptUrl() == null || transaction.getReceiptUrl().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Receipt not available for this transaction"));
            }

            // Get receipt data with caching
            byte[] receiptData = getReceiptDataWithCaching(transaction);

            // Determine content type
            String contentType = determineContentType(transaction.getReceiptUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment",
                    "receipt_" + id + getFileExtension(contentType));
            headers.setContentLength(receiptData.length);

            return new ResponseEntity<>(receiptData, headers, HttpStatus.OK);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving receipt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve receipt: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/receipt/download-url")
    public ResponseEntity<ApiResponse<String>> getReceiptDownloadUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            Transaction transaction = transactionService.getTransactionById(id);

            // Verify that the transaction belongs to the user
            if (!transaction.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You don't have permission to view this receipt"));
            }

            // Check if receipt exists
            if (transaction.getReceiptUrl() == null || transaction.getReceiptUrl().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Receipt not available for this transaction"));
            }

            // Generate download URL
            String downloadUrl = receiptService.getDownloadUrl(transaction.getReceiptUrl());

            return ResponseEntity.ok(
                    ApiResponse.success("Download URL generated successfully", downloadUrl)
            );

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error generating receipt download URL: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate download URL: " + e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<Transaction>>> getRecentTransactions(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            List<Transaction> recentTransactions = transactionService.getRecentTransactions(user, limit);

            return ResponseEntity.ok(
                    ApiResponse.success("Recent transactions retrieved successfully", recentTransactions)
            );
        } catch (Exception e) {
            log.error("Error retrieving recent transactions: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve recent transactions: " + e.getMessage()));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTransactionSummary(
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByUsername(currentUser.getUsername());
            List<Transaction> transactions = transactionService.getUserTransactions(user);

            Map<String, Object> summary = calculateTransactionSummary(transactions);

            return ResponseEntity.ok(
                    ApiResponse.success("Transaction summary retrieved successfully", summary)
            );
        } catch (Exception e) {
            log.error("Error retrieving transaction summary: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transaction summary: " + e.getMessage()));
        }
    }

    // Private helper methods

    @Cacheable(value = "transactionReceipts", key = "#transaction.id + '-data'")
    private byte[] getReceiptDataWithCaching(Transaction transaction) {
        return receiptService.getReceipt(transaction.getReceiptUrl());
    }

    private String determineContentType(String receiptUrl) {
        String lowercaseUrl = receiptUrl.toLowerCase();
        if (lowercaseUrl.endsWith(".pdf")) return "application/pdf";
        if (lowercaseUrl.endsWith(".txt")) return "text/plain";
        if (lowercaseUrl.endsWith(".html")) return "text/html";
        return "application/pdf"; // default
    }

    private String getFileExtension(String contentType) {
        switch (contentType) {
            case "text/plain": return ".txt";
            case "text/html": return ".html";
            default: return ".pdf";
        }
    }

    private Map<String, Object> calculateTransactionSummary(List<Transaction> transactions) {
        Map<String, Object> summary = new HashMap<>();

        // Calculate total deposits
        BigDecimal totalDeposits = transactions.stream()
                .filter(t -> "DEPOSIT".equals(t.getTransactionType()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total withdrawals
        BigDecimal totalWithdrawals = transactions.stream()
                .filter(t -> "WITHDRAWAL".equals(t.getTransactionType()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total fees
        BigDecimal totalFees = transactions.stream()
                .map(Transaction::getFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count transactions by type
        Map<String, Long> transactionsByType = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getTransactionType, Collectors.counting()));

        // Count transactions by status
        Map<String, Long> transactionsByStatus = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()));

        summary.put("totalDeposits", totalDeposits);
        summary.put("totalWithdrawals", totalWithdrawals);
        summary.put("totalFees", totalFees);
        summary.put("transactionCount", transactions.size());
        summary.put("transactionsByType", transactionsByType);
        summary.put("transactionsByStatus", transactionsByStatus);

        return summary;
    }
}