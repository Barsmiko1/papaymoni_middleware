package com.papaymoni.middleware.service;

public interface TransactionEmailService {
    boolean sendTransactionEmail(String to, String subject, String htmlContent);
}