package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.VirtualAccountResponseDto;
import com.papaymoni.middleware.model.User;

import java.io.IOException;

/**
 * Service for interacting with Palmpay Gateway virtual account APIs
 */
public interface PalmpayStaticVirtualAccountService {

    /**
     * Create a virtual account for a user
     *
     * @param user     The user to create an account for
     * @param currency The currency of the account
     * @return VirtualAccountResponse with account details
     * @throws IOException If there's an error communicating with Palmpay Gateway
     */
    Object createVirtualAccount(User user, String currency) throws IOException;

    /**
     * Update the status of a virtual account
     * @param virtualAccountNo The virtual account number to update
     * @param status The new status ("Enabled" or "Disabled")
     * @return true if update was successful
     * @throws IOException If there's an error communicating with Palmpay Gateway
     */
    boolean updateVirtualAccountStatus(String virtualAccountNo, String status) throws IOException;

    /**
     * Delete a virtual account
     * @param virtualAccountNo The virtual account number to delete
     * @return true if deletion was successful
     * @throws IOException If there's an error communicating with Palmpay Gateway
     */
    boolean deleteVirtualAccount(String virtualAccountNo) throws IOException;

    /**
     * Query details for a single virtual account
     * @param virtualAccountNo The virtual account number to query
     * @return VirtualAccountResponse with account details
     * @throws IOException If there's an error communicating with Palmpay Gateway
     */
    VirtualAccountResponseDto queryVirtualAccount(String virtualAccountNo) throws IOException;

    /**
     * Check if the Palmpay Gateway API is available
     * @return true if the API is available
     */
    boolean isServiceAvailable();
}


