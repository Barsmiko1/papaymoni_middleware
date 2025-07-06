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
import java.util.Map;

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

    @Override
    public void sendBelowMinimumDepositEmail(String to, Map<String, Object> model) throws MessagingException {
        String subject = "Deposit Below Minimum Amount";

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("<html><body>");
        contentBuilder.append("<h3>Deposit Below Minimum Amount</h3>");
        contentBuilder.append("<p>Dear Customer,</p>");
        contentBuilder.append("<p>Your deposit was below the minimum required amount.</p>");
        contentBuilder.append("<ul>");

        for (Map.Entry<String, Object> entry : model.entrySet()) {
            contentBuilder.append("<li><strong>")
                    .append(entry.getKey())
                    .append(":</strong> ")
                    .append(entry.getValue())
                    .append("</li>");
        }

        contentBuilder.append("</ul>");
        contentBuilder.append("<p>Please ensure your deposits meet the minimum amount next time.</p>");
        contentBuilder.append("<p>Thank you.</p>");
        contentBuilder.append("</body></html>");

        String content = contentBuilder.toString();

        MimeMessage message = transactionMailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName); // This can throw UnsupportedEncodingException
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            transactionMailSender.send(message);
            log.info("Below minimum deposit email sent to: {}", to);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to encode sender name for email", e);
        }
    }

}

