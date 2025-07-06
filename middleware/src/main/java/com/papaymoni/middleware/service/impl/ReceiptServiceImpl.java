package com.papaymoni.middleware.service.impl;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final S3Service s3Service;

    @Value("${aws.s3.bucket.receipts}")
    private String receiptsBucket;

    private static final String LOGO_URL = "https://papaymoni-logo.s3.us-east-1.amazonaws.com/logo-1/ChatGPT+Image+May+11%2C+2025%2C+09_05_17+PM.png";

    @Override
    public String generateReceipt(Transaction transaction) {
        try {
            // Generate PDF receipt
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);

            document.open();

            // Add logo
            try {
                Image logo = Image.getInstance(new URL(LOGO_URL));
                logo.scaleToFit(150, 150);
                logo.setAlignment(Element.ALIGN_CENTER);
                document.add(logo);
            } catch (Exception e) {
                log.warn("Could not add logo to receipt: {}", e.getMessage());
            }

            // Add title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("TRANSACTION RECEIPT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(10);
            title.setSpacingAfter(20);
            document.add(title);

            // Add receipt number and date
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);

            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setSpacingBefore(10);
            headerTable.setSpacingAfter(20);

            // Receipt Number
            PdfPCell cell = new PdfPCell(new Phrase("Receipt Number:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(cell);

            cell = new PdfPCell(new Phrase("PMP-" + transaction.getId(), normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            headerTable.addCell(cell);

            // Date
            cell = new PdfPCell(new Phrase("Date:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(cell);

            String dateStr = transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            cell = new PdfPCell(new Phrase(dateStr, normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            headerTable.addCell(cell);

            // Transaction Type
            cell = new PdfPCell(new Phrase("Transaction Type:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(cell);

            cell = new PdfPCell(new Phrase(formatTransactionType(transaction.getTransactionType()), normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            headerTable.addCell(cell);

            // Status
            cell = new PdfPCell(new Phrase("Status:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(cell);

            cell = new PdfPCell(new Phrase(transaction.getStatus(), normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            headerTable.addCell(cell);

            document.add(headerTable);

            // Add separator line
            com.itextpdf.text.pdf.draw.LineSeparator line = new com.itextpdf.text.pdf.draw.LineSeparator();
            line.setLineColor(new BaseColor(220, 220, 220));
            document.add(line);

            // Add transaction details
            Paragraph detailsTitle = new Paragraph("Transaction Details", boldFont);
            detailsTitle.setSpacingBefore(15);
            detailsTitle.setSpacingAfter(10);
            document.add(detailsTitle);

            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);

            // Amount
            cell = new PdfPCell(new Phrase("Amount:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPaddingBottom(5);
            detailsTable.addCell(cell);

            String amountStr = transaction.getAmount().setScale(2, RoundingMode.HALF_UP) + " " + transaction.getCurrency();
            cell = new PdfPCell(new Phrase(amountStr, normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cell.setPaddingBottom(5);
            detailsTable.addCell(cell);

            // Fee
            if (transaction.getFee() != null && transaction.getFee().compareTo(BigDecimal.ZERO) > 0) {
                cell = new PdfPCell(new Phrase("Fee:", boldFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);

                String feeStr = transaction.getFee().setScale(2, RoundingMode.HALF_UP) + " " + transaction.getCurrency();
                cell = new PdfPCell(new Phrase(feeStr, normalFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);

                // Total
                cell = new PdfPCell(new Phrase("Total:", boldFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);

                BigDecimal total = transaction.getTransactionType().equals("DEPOSIT")
                        ? transaction.getAmount().subtract(transaction.getFee())
                        : transaction.getAmount().add(transaction.getFee());

                String totalStr = total.setScale(2, RoundingMode.HALF_UP) + " " + transaction.getCurrency();
                cell = new PdfPCell(new Phrase(totalStr, normalFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);
            }

            // Payment Method
            if (transaction.getPaymentMethod() != null) {
                cell = new PdfPCell(new Phrase("Payment Method:", boldFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);

                cell = new PdfPCell(new Phrase(formatPaymentMethod(transaction.getPaymentMethod()), normalFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);
            }

            // Reference
            if (transaction.getExternalReference() != null) {
                cell = new PdfPCell(new Phrase("Reference:", boldFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);

                cell = new PdfPCell(new Phrase(transaction.getExternalReference(), normalFont));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setPaddingBottom(5);
                detailsTable.addCell(cell);
            }

            document.add(detailsTable);

            // Add separator line
            document.add(line);

            // Add user information
            Paragraph userTitle = new Paragraph("User Information", boldFont);
            userTitle.setSpacingBefore(15);
            userTitle.setSpacingAfter(10);
            document.add(userTitle);

            PdfPTable userTable = new PdfPTable(2);
            userTable.setWidthPercentage(100);

            // Name
            cell = new PdfPCell(new Phrase("Name:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPaddingBottom(5);
            userTable.addCell(cell);

            String userName = transaction.getUser().getFirstName() + " " + transaction.getUser().getLastName();
            cell = new PdfPCell(new Phrase(userName, normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cell.setPaddingBottom(5);
            userTable.addCell(cell);

            // Username
            cell = new PdfPCell(new Phrase("Username:", boldFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPaddingBottom(5);
            userTable.addCell(cell);

            cell = new PdfPCell(new Phrase(transaction.getUser().getUsername(), normalFont));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cell.setPaddingBottom(5);
            userTable.addCell(cell);

            document.add(userTable);

            // Add footer
            document.add(Chunk.NEWLINE);
            document.add(line);

            Paragraph footer = new Paragraph("Thank you for using Papaymoni!", normalFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(15);
            document.add(footer);

            Paragraph support = new Paragraph("For any questions, please contact support@papaymoni.com",
                    FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY));
            support.setAlignment(Element.ALIGN_CENTER);
            support.setSpacingBefore(5);
            document.add(support);

            document.close();

            // Convert to bytes
            byte[] receiptBytes = baos.toByteArray();

            // Create unique filename
            String fileName = String.format("receipts/%s/receipt_%s.pdf",
                    transaction.getUser().getId(),
                    transaction.getId());

            // Upload to S3
            String receiptUrl = s3Service.uploadFile(receiptsBucket, fileName,
                    new ByteArrayInputStream(receiptBytes), "application/pdf");

            log.info("Generated receipt for transaction {} at {}", transaction.getId(), receiptUrl);
            return receiptUrl;
        } catch (Exception e) {
            log.error("Error generating receipt for transaction {}: {}", transaction.getId(), e.getMessage(), e);
            // Return null instead of throwing exception to not break the transaction flow
            return null;
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

    private String formatTransactionType(String transactionType) {
        if (transactionType == null) return "";

        switch (transactionType) {
            case "DEPOSIT":
                return "Deposit";
            case "WITHDRAWAL":
                return "Withdrawal";
            case "FEE":
                return "Fee";
            default:
                return transactionType;
        }
    }

    private String formatPaymentMethod(String paymentMethod) {
        if (paymentMethod == null) return "";

        switch (paymentMethod) {
            case "CRYPTO_TRANSFER":
                return "Cryptocurrency Transfer";
            case "BANK_TRANSFER":
                return "Bank Transfer";
            default:
                return paymentMethod;
        }
    }
}
