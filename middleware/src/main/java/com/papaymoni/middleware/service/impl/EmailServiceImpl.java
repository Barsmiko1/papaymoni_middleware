package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    /**
     * Send a simple email message
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     * @return true if sent successfully
     */
    @Override
    public boolean sendSimpleMessage(String to, String subject, String text) {
        try {
            log.info("Sending simple email to: {}", to);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            emailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            return false;
        }
    }

    /**
     * Send an email with HTML content
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent email body in HTML format
     * @return true if sent successfully
     */
    @Override
    public boolean sendHtmlMessage(String to, String subject, String htmlContent) {
        try {
            log.info("Sending HTML email to: {}", to);
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            emailSender.send(message);
            log.info("HTML email sent successfully to: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to: {}", to, e);
            return false;
        }
    }
}