package com.papaymoni.middleware.service.impl;

import com.papaymoni.middleware.service.TransactionEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

@Slf4j
@Service
public class TransactionEmailServiceImpl implements TransactionEmailService {

    private final JavaMailSender transactionMailSender;

    @Value("${spring.mail.transactions.from.address:alert@papaymoni.com}")
    private String fromAddress;

    @Value("${spring.mail.transactions.from.name:Papaymoni Alerts}")
    private String fromName;

    public TransactionEmailServiceImpl(@Qualifier("transactionMailSender") JavaMailSender transactionMailSender) {
        this.transactionMailSender = transactionMailSender;
    }

    @Override
    public boolean sendTransactionEmail(String to, String subject, String htmlContent) {
        try {
            log.info("Sending transaction email to: {} from: {}", to, fromAddress);
            MimeMessage message = transactionMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            transactionMailSender.send(message);
            log.info("Transaction email sent successfully to: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send transaction email to: {}", to, e);
            return false;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}