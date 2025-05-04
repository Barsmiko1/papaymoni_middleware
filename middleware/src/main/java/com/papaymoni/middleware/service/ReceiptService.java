package com.papaymoni.middleware.service;

import com.papaymoni.middleware.model.Transaction;

public interface ReceiptService {
    String generateReceipt(Transaction transaction);
    byte[] getReceipt(String receiptUrl);
    String getDownloadUrl(String receiptUrl);
}