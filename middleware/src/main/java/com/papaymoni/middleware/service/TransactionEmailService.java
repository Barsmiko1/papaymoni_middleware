package com.papaymoni.middleware.service;

import javax.mail.MessagingException;
import java.util.Map;

public interface TransactionEmailService {
    boolean sendTransactionEmail(String to, String subject, String htmlContent);

    // Add method for sending below minimum deposit email
    void sendBelowMinimumDepositEmail(String to, Map<String, Object> model) throws MessagingException;
}