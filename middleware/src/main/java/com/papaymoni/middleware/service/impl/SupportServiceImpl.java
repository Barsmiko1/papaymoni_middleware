package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.dto.ApiResponse;
import com.papaymoni.middleware.dto.SupportRequestDto;
import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.SupportTicket;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.SupportTicketRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.SupportEmailService;
import com.papaymoni.middleware.service.SupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final SupportEmailService supportEmailService;

    @Value("${support.email}")
    private String supportEmail;

    @Value("${support.team.emails}")
    private String[] supportTeamEmails;

    @Override
    @Transactional
    public ApiResponse<SupportTicket> createSupportTicket(SupportRequestDto requestDto) {
        log.info("Creating support ticket for email: {}", requestDto.getEmail());

        SupportTicket ticket = new SupportTicket();
        ticket.setEmail(requestDto.getEmail());
        ticket.setSubject(requestDto.getSubject());
        ticket.setMessage(requestDto.getMessage());
        ticket.setName(requestDto.getName());
        ticket.setPhoneNumber(requestDto.getPhoneNumber());
        ticket.setStatus("OPEN");
        ticket.setTicketNumber(generateTicketNumber());

        // Associate with user if they exist
        Optional<User> userOpt = userRepository.findByEmail(requestDto.getEmail());
        userOpt.ifPresent(ticket::setUser);

        SupportTicket savedTicket = supportTicketRepository.save(ticket);

        // Send acknowledgment email to user
        sendAcknowledgmentEmail(savedTicket);

        // Forward request to support team
        forwardToSupportTeam(savedTicket);

        log.info("Support ticket created successfully with number: {}", savedTicket.getTicketNumber());
        return ApiResponse.success("Support request submitted successfully", savedTicket);
    }

    @Override
    public SupportTicket getTicketByNumber(String ticketNumber) {
        return supportTicketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found with number: " + ticketNumber));
    }

    @Override
    @Transactional
    public SupportTicket updateTicketStatus(String ticketNumber, String status) {
        SupportTicket ticket = getTicketByNumber(ticketNumber);

        if (!isValidStatus(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        ticket.setStatus(status.toUpperCase());

        // Notify user of status change if status is RESOLVED
        if ("RESOLVED".equals(status.toUpperCase())) {
            sendStatusUpdateEmail(ticket);
        }

        return supportTicketRepository.save(ticket);
    }

    @Override
    public List<SupportTicket> getTicketsByEmail(String email) {
        return supportTicketRepository.findByEmail(email);
    }

    @Override
    public List<SupportTicket> getTicketsByStatus(String status) {
        return supportTicketRepository.findByStatus(status.toUpperCase());
    }

    private String generateTicketNumber() {
        // Format: PMSUP-[TIMESTAMP]-[RANDOM]
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String random = UUID.randomUUID().toString().substring(0, 8);
        return "PMSUP-" + timestamp + "-" + random;
    }

    private boolean isValidStatus(String status) {
        String upperStatus = status.toUpperCase();
        return "OPEN".equals(upperStatus) || "INPROGRESS".equals(upperStatus) || "RESOLVED".equals(upperStatus);
    }

    private void sendAcknowledgmentEmail(SupportTicket ticket) {
        String subject = "Papaymoni Support - Ticket #" + ticket.getTicketNumber();
        String htmlContent = createAcknowledgmentEmailTemplate(ticket);

        boolean sent = supportEmailService.sendHtmlMessage(ticket.getEmail(), subject, htmlContent);
        if (!sent) {
            log.error("Failed to send acknowledgment email for ticket: {}", ticket.getTicketNumber());
        }
    }

    private void forwardToSupportTeam(SupportTicket ticket) {
        String subject = "New Support Request - Ticket #" + ticket.getTicketNumber();
        String htmlContent = createSupportTeamEmailTemplate(ticket);

        boolean sent = supportEmailService.sendHtmlMessageWithCc(supportEmail, supportTeamEmails, subject, htmlContent);
        if (!sent) {
            log.error("Failed to forward support request to team for ticket: {}", ticket.getTicketNumber());
        }
    }

    private void sendStatusUpdateEmail(SupportTicket ticket) {
        String subject = "Papaymoni Support - Ticket #" + ticket.getTicketNumber() + " Resolved";
        String htmlContent = createStatusUpdateEmailTemplate(ticket);

        boolean sent = supportEmailService.sendHtmlMessage(ticket.getEmail(), subject, htmlContent);
        if (!sent) {
            log.error("Failed to send status update email for ticket: {}", ticket.getTicketNumber());
        }
    }

    private String createAcknowledgmentEmailTemplate(SupportTicket ticket) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #1a73e8; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".ticket-details { background-color: #fff; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
                ".footer { text-align: center; padding: 20px; font-size: 12px; color: #666; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Papaymoni Support</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<p>Dear " + (ticket.getName() != null ? ticket.getName() : "Customer") + ",</p>" +
                "<p>Thank you for contacting Papaymoni Support. We have received your request and will respond as soon as possible.</p>" +
                "<div class='ticket-details'>" +
                "<p><strong>Ticket Number:</strong> " + ticket.getTicketNumber() + "</p>" +
                "<p><strong>Subject:</strong> " + ticket.getSubject() + "</p>" +
                "<p><strong>Status:</strong> " + ticket.getStatus() + "</p>" +
                "<p><strong>Date Submitted:</strong> " + ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss")) + "</p>" +
                "</div>" +
                "<p>Please keep your ticket number for future reference. You can reply to this email if you have additional information to add to your request.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© " + LocalDateTime.now().getYear() + " Papaymoni. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String createSupportTeamEmailTemplate(SupportTicket ticket) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #1a73e8; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".ticket-details { background-color: #fff; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
                ".message { background-color: #fff; padding: 15px; border-radius: 5px; margin: 15px 0; white-space: pre-wrap; }" +
                ".footer { text-align: center; padding: 20px; font-size: 12px; color: #666; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>New Support Request</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<div class='ticket-details'>" +
                "<p><strong>Ticket Number:</strong> " + ticket.getTicketNumber() + "</p>" +
                "<p><strong>Subject:</strong> " + ticket.getSubject() + "</p>" +
                "<p><strong>Customer Email:</strong> " + ticket.getEmail() + "</p>" +
                "<p><strong>Customer Name:</strong> " + (ticket.getName() != null ? ticket.getName() : "Not provided") + "</p>" +
                "<p><strong>Phone Number:</strong> " + (ticket.getPhoneNumber() != null ? ticket.getPhoneNumber() : "Not provided") + "</p>" +
                "<p><strong>Date Submitted:</strong> " + ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss")) + "</p>" +
                "</div>" +
                "<p><strong>Customer Message:</strong></p>" +
                "<div class='message'>" + ticket.getMessage() + "</div>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© " + LocalDateTime.now().getYear() + " Papaymoni Support System</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String createStatusUpdateEmailTemplate(SupportTicket ticket) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #1a73e8; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".ticket-details { background-color: #fff; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
                ".status-resolved { color: #28a745; font-weight: bold; }" +
                ".footer { text-align: center; padding: 20px; font-size: 12px; color: #666; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Papaymoni Support</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<p>Dear " + (ticket.getName() != null ? ticket.getName() : "Customer") + ",</p>" +
                "<p>We are pleased to inform you that your support request has been resolved.</p>" +
                "<div class='ticket-details'>" +
                "<p><strong>Ticket Number:</strong> " + ticket.getTicketNumber() + "</p>" +
                "<p><strong>Subject:</strong> " + ticket.getSubject() + "</p>" +
                "<p><strong>Status:</strong> <span class='status-resolved'>" + ticket.getStatus() + "</span></p>" +
                "<p><strong>Date Resolved:</strong> " + ticket.getUpdatedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss")) + "</p>" +
                "</div>" +
                "<p>If you have any further questions or if you feel your issue has not been fully resolved, please reply to this email.</p>" +
                "<p>Thank you for using Papaymoni!</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© " + LocalDateTime.now().getYear() + " Papaymoni. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
