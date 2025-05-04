package com.papaymoni.middleware.service.impl;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import com.papaymoni.middleware.model.Transaction;
import com.papaymoni.middleware.service.ReceiptService;
import com.papaymoni.middleware.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final S3Service s3Service;

    @Value("${aws.s3.bucket.receipts}")
    private String receiptsBucket;

    @Override
    public String generateReceipt(Transaction transaction) {
        try {
            // Generate receipt content
            String receiptContent = generateReceiptContent(transaction);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, baos);

            // Convert to bytes
            byte[] receiptBytes = receiptContent.getBytes();

            // Create unique filename
            String fileName = String.format("receipts/%s/receipt_%s.txt",
                    transaction.getUser().getId(),
                    transaction.getId());

            // Upload to S3
            String receiptUrl = s3Service.uploadFile(receiptsBucket, fileName,
                    new ByteArrayInputStream(receiptBytes), "text/plain");

            log.info("Generated receipt for transaction {} at {}", transaction.getId(), receiptUrl);
            return receiptUrl;
        } catch (Exception e) {
            log.error("Error generating receipt for transaction {}: {}", transaction.getId(), e.getMessage());
            throw new RuntimeException("Failed to generate receipt", e);
        }
    }

    @Override
    public byte[] getReceipt(String receiptUrl) {
        try {
            // Extract bucket and key from URL
            String key = extractKeyFromUrl(receiptUrl);
            return s3Service.downloadFile(receiptsBucket, key);
        } catch (Exception e) {
            log.error("Error downloading receipt: {}", e.getMessage());
            throw new RuntimeException("Failed to download receipt", e);
        }
    }

    @Override
    public String getDownloadUrl(String receiptUrl) {
        try {
            String key = extractKeyFromUrl(receiptUrl);
            return s3Service.generatePresignedUrl(receiptsBucket, key, 60); // 1 hour expiration
        } catch (Exception e) {
            log.error("Error generating download URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    private String generateReceiptContent(Transaction transaction) {
        StringBuilder content = new StringBuilder();
        content.append("PAPAYMONI TRANSACTION RECEIPT\n");
        content.append("=============================\n\n");
        content.append("Transaction ID: ").append(transaction.getId()).append("\n");
        content.append("Date: ").append(transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        content.append("Type: ").append(transaction.getTransactionType()).append("\n");
        content.append("Amount: ").append(transaction.getAmount()).append(" ").append(transaction.getCurrency()).append("\n");
        content.append("Fee: ").append(transaction.getFee()).append(" ").append(transaction.getCurrency()).append("\n");
        content.append("Status: ").append(transaction.getStatus()).append("\n");
        content.append("Reference: ").append(transaction.getExternalReference()).append("\n");
        content.append("\n");
        content.append("User: ").append(transaction.getUser().getUsername()).append("\n");
        content.append("\n");
        content.append("Thank you for using Papaymoni!\n");

        return content.toString();
    }

    private String extractKeyFromUrl(String url) {
        // Extract the key from S3 URL
        // Example: https://bucket-name.s3.region.amazonaws.com/receipts/123/receipt_456.txt
        String[] parts = url.split("/");
        StringBuilder key = new StringBuilder();
        boolean startCollecting = false;
        for (String part : parts) {
            if (startCollecting) {
                if (key.length() > 0) key.append("/");
                key.append(part);
            }
            if (part.contains("amazonaws.com")) {
                startCollecting = true;
            }
        }
        return key.toString();
    }
}