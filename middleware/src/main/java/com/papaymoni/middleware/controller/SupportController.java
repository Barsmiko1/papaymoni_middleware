package com.papaymoni.middleware.controller;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.SupportRequestDto;
import com.papaymoni.middleware.model.SupportTicket;
import com.papaymoni.middleware.service.SupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/support")
@Slf4j
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<SupportTicket>> submitSupportRequest(
            @Valid @RequestBody SupportRequestDto requestDto) {
        log.info("Received support request from: {}", requestDto.getEmail());
        ApiResponse<SupportTicket> response = supportService.createSupportTicket(requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/ticket/{ticketNumber}")
    public ResponseEntity<ApiResponse<SupportTicket>> getTicketByNumber(
            @PathVariable String ticketNumber) {
        log.info("Fetching support ticket with number: {}", ticketNumber);
        SupportTicket ticket = supportService.getTicketByNumber(ticketNumber);
        return ResponseEntity.ok(ApiResponse.success("Ticket retrieved successfully", ticket));
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<ApiResponse<List<SupportTicket>>> getTicketsByEmail(
            @PathVariable String email) {
        log.info("Fetching support tickets for email: {}", email);
        List<SupportTicket> tickets = supportService.getTicketsByEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Tickets retrieved successfully", tickets));
    }

    @PutMapping("/ticket/{ticketNumber}")
    public ResponseEntity<ApiResponse<SupportTicket>> updateTicketStatus(
            @PathVariable String ticketNumber,
            @RequestParam String status) {
        log.info("Updating status of ticket {} to {}", ticketNumber, status);
        SupportTicket updatedTicket = supportService.updateTicketStatus(ticketNumber, status);
        return ResponseEntity.ok(ApiResponse.success("Ticket status updated successfully", updatedTicket));
    }
}
