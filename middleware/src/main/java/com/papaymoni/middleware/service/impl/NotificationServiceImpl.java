package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.exception.ResourceNotFoundException;
import com.papaymoni.middleware.model.Notification;
import com.papaymoni.middleware.model.User;
import com.papaymoni.middleware.repository.NotificationRepository;
import com.papaymoni.middleware.repository.UserRepository;
import com.papaymoni.middleware.service.EmailService;
import com.papaymoni.middleware.service.NotificationService;
import com.papaymoni.middleware.service.TransactionEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final TransactionEmailService transactionEmailService;

    @Override
    @Transactional
    public Notification createNotification(User user, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setStatus("PENDING");

        Notification savedNotification = notificationRepository.save(notification);

        if ("EMAIL".equalsIgnoreCase(type)) {
            boolean sent = sendEmail(user.getEmail(), title, message);
            if (sent) {
                savedNotification.setStatus("SENT");
                savedNotification.setSentAt(LocalDateTime.now());
            } else {
                savedNotification.setStatus("FAILED");
            }
        } else if ("SMS".equalsIgnoreCase(type)) {
            sendSms(user.getPhoneNumber(), message);
            savedNotification.setStatus("SENT");
            savedNotification.setSentAt(LocalDateTime.now());
        }

        return notificationRepository.save(savedNotification);
    }

    @Override
    @Transactional
    public Notification createNotificationForUser(Long userId, String type, String title, String message) {
        User userRef = userRepository.getOne(userId);
        return createNotification(userRef, type, title, message);
    }

    @Override
    public boolean sendEmail(String to, String subject, String body) {
        try {
            // For transaction notifications, use transaction-specific email service
            if (subject.contains("Deposit") || subject.contains("Withdrawal") || subject.contains("Transaction")) {
                String htmlContent = createTransactionEmailTemplate(subject, body);
                return transactionEmailService.sendTransactionEmail(to, subject, htmlContent);
            } else {
                // For other notifications, use regular email service
                return emailService.sendSimpleMessage(to, subject, body);
            }
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendTransactionEmail(String to, String subject, String body, String transactionId) {
        String enhancedSubject = subject + " - Reference: " + transactionId;
        String htmlContent = createTransactionEmailTemplate(enhancedSubject, body);
        return transactionEmailService.sendTransactionEmail(to, enhancedSubject, htmlContent);
    }

    @Override
    @Transactional
    public boolean sendDepositNotification(User user, BigDecimal amount, String currency, Long transactionId) {
        String title = "Deposit Received - Transaction #" + transactionId;
        String message = String.format(
                "Dear %s,\n\n" +
                        "Your deposit has been successfully processed:\n\n" +
                        "Amount: %s %s\n" +
                        "Transaction ID: %s\n" +
                        "Status: Completed\n\n" +
                        "The funds have been credited to your %s wallet.\n\n" +
                        "Best regards,\n" +
                        "Papaymoni Team",
                user.getFirstName(),
                amount.setScale(2, RoundingMode.HALF_UP),
                currency,
                transactionId,
                currency
        );

        // Create notification record
        Notification notification = createNotification(user, "EMAIL", title, message);

        // Send email
        boolean sent = sendTransactionEmail(user.getEmail(), title, message, transactionId.toString());

        if (sent) {
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus("FAILED");
        }

        return notificationRepository.save(notification).getStatus().equals("SENT");
    }

    @Override
    public boolean sendWithdrawalNotification(Long userId, String email, BigDecimal amount, String currency, Long transactionId, BigDecimal fee) {
        return false;
    }

    @Override
    @Transactional
    public boolean sendWithdrawalNotification(User user, BigDecimal amount, String currency,
                                              Long transactionId, BigDecimal fee) {
        String title = "Withdrawal Processed - Transaction #" + transactionId;
        String message = String.format(
                "Dear %s,\n\n" +
                        "Your withdrawal has been successfully processed:\n\n" +
                        "Amount: %s %s\n" +
                        "Fee: %s %s\n" +
                        "Total Deducted: %s %s\n" +
                        "Transaction ID: %s\n" +
                        "Status: Completed\n\n" +
                        "The funds have been transferred from your %s wallet.\n\n" +
                        "Best regards,\n" +
                        "Papaymoni Team",
                user.getFirstName(),
                amount.setScale(2, RoundingMode.HALF_UP),
                currency,
                fee.setScale(2, RoundingMode.HALF_UP),
                currency,
                amount.add(fee).setScale(2, RoundingMode.HALF_UP),
                currency,
                transactionId,
                currency
        );

        // Create notification record
        Notification notification = createNotification(user, "EMAIL", title, message);

        // Send email
        boolean sent = sendTransactionEmail(user.getEmail(), title, message, transactionId.toString());

        if (sent) {
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus("FAILED");
        }

        return notificationRepository.save(notification).getStatus().equals("SENT");
    }

    @Override
    @Transactional
    public boolean sendOrderNotification(User user, String orderStatus, String orderId,
                                         BigDecimal amount, String currency) {
        String title = "Order " + orderStatus + " - Order #" + orderId;
        String message = String.format(
                "Dear %s,\n\n" +
                        "Your order has been %s:\n\n" +
                        "Order ID: %s\n" +
                        "Amount: %s %s\n" +
                        "Status: %s\n\n" +
                        "Thank you for using Papaymoni!\n\n" +
                        "Best regards,\n" +
                        "Papaymoni Team",
                user.getFirstName(),
                orderStatus.toLowerCase(),
                orderId,
                amount.setScale(2, RoundingMode.HALF_UP),
                currency,
                orderStatus
        );

        // Create notification record
        Notification notification = createNotification(user, "EMAIL", title, message);

        // Send email
        boolean sent = sendTransactionEmail(user.getEmail(), title, message, orderId);

        if (sent) {
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus("FAILED");
        }

        return notificationRepository.save(notification).getStatus().equals("SENT");
    }

    @Override
    public boolean sendVerificationEmail(String to, String verificationCode) {
        String subject = "Papaymoni - Email Verification";
        String htmlContent = createVerificationEmailTemplate(verificationCode);
        return emailService.sendHtmlMessage(to, subject, htmlContent);
    }

    @Override
    public boolean sendPasswordResetEmail(String to, String resetToken) {
        String subject = "Papaymoni - Password Reset Request";
        String htmlContent = createPasswordResetEmailTemplate(resetToken);
        return emailService.sendHtmlMessage(to, subject, htmlContent);
    }

    private String createTransactionEmailTemplate(String subject, String body) {
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss"));

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #1a73e8; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".footer { text-align: center; padding: 20px; font-size: 12px; color: #666; }" +
                ".transaction-details { background-color: #fff; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
                ".amount { font-size: 24px; font-weight: bold; color: #1a73e8; }" +
                ".button { background-color: #1a73e8; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Papaymoni</h1>" +
                "<p>" + subject + "</p>" +
                "</div>" +
                "<div class='content'>" +
                "<div class='transaction-details'>" +
                "<p>" + body.replace("\n", "<br>") + "</p>" +
                "<p><strong>Date:</strong> " + currentDateTime + "</p>" +
                "</div>" +
                "<p>If you have any questions about this transaction, please contact our support team.</p>" +
                "<p>Thank you for using Papaymoni!</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>This is an automated message from Papaymoni Alert System</p>" +
                "<p>© " + LocalDateTime.now().getYear() + " Papaymoni. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String createVerificationEmailTemplate(String verificationCode) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #1a73e8; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".code { font-size: 32px; font-weight: bold; color: #1a73e8; text-align: center; padding: 20px; background-color: #fff; border-radius: 5px; margin: 20px 0; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Papaymoni</h1>" +
                "<p>Email Verification</p>" +
                "</div>" +
                "<div class='content'>" +
                "<p>Thank you for registering with Papaymoni!</p>" +
                "<p>Please use the following verification code to complete your registration:</p>" +
                "<div class='code'>" + verificationCode + "</div>" +
                "<p>This code will expire in 15 minutes.</p>" +
                "<p>If you did not request this verification, please ignore this email.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String createPasswordResetEmailTemplate(String resetToken) {
        String resetLink = "https://papaymoni.com/reset-password?token=" + resetToken;

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #1a73e8; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".button { background-color: #1a73e8; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 20px 0; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Papaymoni</h1>" +
                "<p>Password Reset Request</p>" +
                "</div>" +
                "<div class='content'>" +
                "<p>We received a request to reset your password.</p>" +
                "<p>Click the button below to reset your password:</p>" +
                "<a href='" + resetLink + "' class='button'>Reset Password</a>" +
                "<p>If you didn't request this password reset, please ignore this email.</p>" +
                "<p>This link will expire in 1 hour.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    @Override
    public List<Notification> getUserNotifications(User user) {
        return notificationRepository.findByUser(user);
    }

    @Override
    public List<Notification> getUserUnreadNotifications(User user) {
        return notificationRepository.findByUserAndIsRead(user, false);
    }

    @Override
    @Transactional
    public void markNotificationAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Override
    public long countUnreadNotifications(User user) {
        return notificationRepository.countByUserAndIsRead(user, false);
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        // SMS implementation remains the same for now
        System.out.println("Sending SMS to: " + phoneNumber);
        System.out.println("Message: " + message);
    }
}
