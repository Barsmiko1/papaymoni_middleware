package com.papaymoni.middleware.service;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.SupportRequestDto;
import com.papaymoni.middleware.model.SupportTicket;

import java.util.List;

public interface SupportService {
    /**
     * Create a new support ticket
     * @param requestDto the support request details
     * @return ApiResponse with the created ticket
     */
    ApiResponse<SupportTicket> createSupportTicket(SupportRequestDto requestDto);

    /**
     * Get a support ticket by its ticket number
     * @param ticketNumber the ticket number
     * @return the support ticket
     */
    SupportTicket getTicketByNumber(String ticketNumber);

    /**
     * Update the status of a support ticket
     * @param ticketNumber the ticket number
     * @param status the new status
     * @return the updated ticket
     */
    SupportTicket updateTicketStatus(String ticketNumber, String status);

    /**
     * Get all support tickets for a user
     * @param email the user's email
     * @return list of support tickets
     */
    List<SupportTicket> getTicketsByEmail(String email);

    /**
     * Get all support tickets with a specific status
     * @param status the status to filter by
     * @return list of support tickets
     */
    List<SupportTicket> getTicketsByStatus(String status);
}
