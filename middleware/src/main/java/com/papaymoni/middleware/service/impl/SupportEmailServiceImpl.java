package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.service.SupportEmailService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Slf4j
@Service
public class SupportEmailServiceImpl implements SupportEmailService {

    private final JavaMailSender supportMailSender;

    @Value("${spring.support.mail.username}")
    private String fromEmail;

    @Value("${spring.support.mail.sender.name}")
    private String senderName;

    public SupportEmailServiceImpl(@Qualifier("supportMailSender") JavaMailSender supportMailSender) {
        this.supportMailSender = supportMailSender;
    }

    @Override
    public boolean sendSimpleMessage(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", senderName, fromEmail));
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            supportMailSender.send(message);
            log.info("Support email sent to: {}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send support email to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendHtmlMessage(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = supportMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(String.format("%s <%s>", senderName, fromEmail));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            supportMailSender.send(message);
            log.info("Support HTML email sent to: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send support HTML email to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendHtmlMessageWithCc(String to, String[] cc, String subject, String htmlContent) {
        try {
            MimeMessage message = supportMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(String.format("%s <%s>", senderName, fromEmail));
            helper.setTo(to);
            helper.setCc(cc);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            supportMailSender.send(message);
            log.info("Support HTML email with CC sent to: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send support HTML email with CC to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendHtmlMessageWithAttachment(String to, String subject, String htmlContent,
                                                 String attachmentFilename, byte[] attachmentData, String mimeType) {
        try {
            MimeMessage message = supportMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(String.format("%s <%s>", senderName, fromEmail));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            // Add attachment
            ByteArrayResource resource = new ByteArrayResource(attachmentData);
            helper.addAttachment(attachmentFilename, resource, mimeType);

            supportMailSender.send(message);
            log.info("Support HTML email with attachment sent to: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send support HTML email with attachment to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
