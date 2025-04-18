package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Implementation of BankService
 * Handles bank operations like transfers and name enquiry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankServiceImpl implements BankService {

    /**
     * Validate recipient details via name enquiry
     * @param recipientName the recipient name
     * @param accountDetails the account details
     * @return true if recipient is valid
     */
    @Override
    public boolean validateRecipient(String recipientName, String accountDetails) {
        log.info("Validating recipient: {} with account details: {}", recipientName, accountDetails);

        // In a real implementation, this would call a bank API to validate the account
        // For now, we'll just return true

        // Parse account details (format: "bankCode:accountNumber")
        String[] parts = accountDetails.split(":");
        if (parts.length != 2) {
            log.warn("Invalid account details format: {}", accountDetails);
            return false;
        }

        String bankCode = parts[0];
        String accountNumber = parts[1];

        log.info("Validated recipient for bank code: {} and account number: {}", bankCode, accountNumber);
        return true;
    }

    /**
     * Process a bank transfer
     * @param amount the amount to transfer
     * @param accountDetails the recipient account details
     * @param recipientName the recipient name
     * @param description the transfer description
     * @return the transfer reference
     */
    @Override
    public String processTransfer(BigDecimal amount, String accountDetails, String recipientName, String description) {
        log.info("Processing transfer of {} to {} ({}): {}",
                amount, recipientName, accountDetails, description);

        // In a real implementation, this would call a bank API to process the transfer
        // For now, we'll just generate a reference

        // Parse account details (format: "bankCode:accountNumber")
        String[] parts = accountDetails.split(":");
        if (parts.length != 2) {
            log.warn("Invalid account details format: {}", accountDetails);
            throw new IllegalArgumentException("Invalid account details format");
        }

        String bankCode = parts[0];
        String accountNumber = parts[1];

        // Generate a unique reference for the transfer
        String reference = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("Transfer processed with reference: {} to bank code: {} and account number: {}",
                reference, bankCode, accountNumber);

        return reference;
    }
}
