package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.InternalTransferRequestDto;
import com.papaymoni.middleware.dto.InternalTransferResponseDto;
import com.papaymoni.middleware.dto.UsernameValidationDto;
import com.papaymoni.middleware.model.User;

public interface InternalTransferService {

    /**
     * Validate recipient username and return their full name
     * @param username the username to validate
     * @return validation result with full name if valid
     */
    UsernameValidationDto validateRecipientUsername(String username);

    /**
     * Process internal transfer between users
     * @param sender the sender user
     * @param transferRequest the transfer request details
     * @return transfer response with transaction details
     */
    InternalTransferResponseDto processInternalTransfer(User sender, InternalTransferRequestDto transferRequest);
}
