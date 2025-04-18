package com.papaymoni.middleware.service;

import java.math.BigDecimal;

/**
 * Service interface for bank operations
 */
public interface BankService {

    /**
     * Validate recipient details via name enquiry
     * @param recipientName the recipient name
     * @param accountDetails the account details
     * @return true if recipient is valid
     */
    boolean validateRecipient(String recipientName, String accountDetails);

    /**
     * Process a bank transfer
     * @param amount the amount to transfer
     * @param accountDetails the recipient account details
     * @param recipientName the recipient name
     * @param description the transfer description
     * @return the transfer reference
     */
    String processTransfer(BigDecimal amount, String accountDetails, String recipientName, String description);
}
