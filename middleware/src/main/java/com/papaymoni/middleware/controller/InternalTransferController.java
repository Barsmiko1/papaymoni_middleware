package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.InternalTransferRequestDto;
import com.papaymoni.middleware.dto.InternalTransferResponseDto;
import com.papaymoni.middleware.dto.UsernameValidationDto;
import com.papaymoni.middleware.exception.InsufficientBalanceException;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.service.InternalTransferService;
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

@Slf4j
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class InternalTransferController {

    private final InternalTransferService internalTransferService;
    private final UserService userService;

    /**
     * Validate recipient username and return their full name
     */
    @GetMapping("/validate-username/{username}")
    public ResponseEntity<ApiResponse<UsernameValidationDto>> validateUsername(@PathVariable String username) {
        try {
            log.info("Validating username: {}", username);

            UsernameValidationDto validation = internalTransferService.validateRecipientUsername(username);

            if (validation.isValid()) {
                return ResponseEntity.ok(ApiResponse.success("Username validated successfully", validation));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(validation.getMessage(), validation));
            }
        } catch (Exception e) {
            log.error("Error validating username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error validating username: " + e.getMessage()));
        }
    }

    /**
     * Process internal transfer between users
     */
    @PostMapping("/internal")
    public ResponseEntity<ApiResponse<InternalTransferResponseDto>> processInternalTransfer(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody InternalTransferRequestDto transferRequest) {

        try {
            log.info("Processing internal transfer from user: {} to: {}",
                    currentUser.getUsername(), transferRequest.getRecipientUsername());

            // Get sender user
            User sender = userService.getUserByUsername(currentUser.getUsername());

            // Validate transfer request
            if (transferRequest.getAmount().compareTo(new BigDecimal("0.01")) < 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Minimum transfer amount is 0.01"));
            }

            // Process the transfer
            InternalTransferResponseDto response = internalTransferService.processInternalTransfer(sender, transferRequest);

            return ResponseEntity.ok(ApiResponse.success("Transfer completed successfully", response));

        } catch (ResourceNotFoundException e) {
            log.error("Recipient not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid transfer request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing internal transfer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process transfer: " + e.getMessage()));
        }
    }
}
