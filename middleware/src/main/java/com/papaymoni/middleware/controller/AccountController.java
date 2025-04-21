package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.VirtualAccountDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.model.VirtualAccount;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.VirtualAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for account management functions
 * Handles virtual accounts, balances, and account-related operations
 */
@Slf4j
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final VirtualAccountService virtualAccountService;
    private final UserRepository userRepository;

    public AccountController(VirtualAccountService virtualAccountService, UserRepository userRepository) {
        this.virtualAccountService = virtualAccountService;
        this.userRepository = userRepository;
    }

    /**
     * Get all virtual accounts for the authenticated user
     */
    @GetMapping("/virtual")
    public ResponseEntity<ApiResponse> getVirtualAccounts(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            log.debug("Fetching virtual accounts for user: {}", currentUser.getUsername());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            List<VirtualAccount> accounts = virtualAccountService.getUserVirtualAccounts(user);

            return ResponseEntity.ok(ApiResponse.success("Virtual accounts retrieved successfully", accounts));
        } catch (ResourceNotFoundException e) {
            log.error("User not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), "USER_NOT_FOUND"));
        } catch (Exception e) {
            log.error("Error retrieving virtual accounts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve virtual accounts: " + e.getMessage(),
                            "VIRTUAL_ACCOUNTS_ERROR"));
        }
    }

    /**
     * Create a new virtual account for the authenticated user
     */
    @PostMapping("/virtual")
    public ResponseEntity<ApiResponse> createVirtualAccount(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody VirtualAccountDto accountDto) {

        try {
            log.debug("Creating virtual account for user: {} with currency: {}",
                    currentUser.getUsername(), accountDto.getCurrency());

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            VirtualAccount account = virtualAccountService.createVirtualAccount(user, accountDto.getCurrency());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Virtual account created successfully", account));
        } catch (ResourceNotFoundException e) {
            log.error("User not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), "USER_NOT_FOUND"));
        } catch (Exception e) {
            log.error("Error creating virtual account: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create virtual account: " + e.getMessage(),
                            "CREATE_ACCOUNT_ERROR"));
        }
    }

    /**
     * Get virtual accounts filtered by currency
     */
    @GetMapping("/virtual/currency/{currency}")
    public ResponseEntity<ApiResponse> getVirtualAccountsByCurrency(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable String currency) {

        try {
            log.debug("Fetching virtual accounts for user: {} with currency: {}",
                    currentUser.getUsername(), currency);

            User user = userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            List<VirtualAccount> accounts = virtualAccountService.getUserVirtualAccountsByCurrency(user, currency);

            return ResponseEntity.ok(ApiResponse.success(
                    "Virtual accounts with currency " + currency + " retrieved successfully", accounts));
        } catch (ResourceNotFoundException e) {
            log.error("User not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), "USER_NOT_FOUND"));
        } catch (Exception e) {
            log.error("Error retrieving virtual accounts by currency: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve virtual accounts: " + e.getMessage(),
                            "VIRTUAL_ACCOUNTS_ERROR"));
        }
    }

    /**
     * Get a specific virtual account by ID
     */
    @GetMapping("/virtual/{id}")
    public ResponseEntity<ApiResponse> getVirtualAccountById(
            @AuthenticationPrincipal UserDetails currentUser,
            @PathVariable Long id) {

        try {
            log.debug("Fetching virtual account with ID: {} for user: {}", id, currentUser.getUsername());

            // First check if user exists
            userRepository.findByUsername(currentUser.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            VirtualAccount account = virtualAccountService.getVirtualAccountById(id);

            // Security check: make sure the account belongs to the authenticated user
            if (!account.getUser().getUsername().equals(currentUser.getUsername())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You do not have permission to access this account", "ACCESS_DENIED"));
            }

            return ResponseEntity.ok(ApiResponse.success("Virtual account retrieved successfully", account));
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), "RESOURCE_NOT_FOUND"));
        } catch (Exception e) {
            log.error("Error retrieving virtual account: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve virtual account: " + e.getMessage(),
                            "GET_ACCOUNT_ERROR"));
        }
    }
}
