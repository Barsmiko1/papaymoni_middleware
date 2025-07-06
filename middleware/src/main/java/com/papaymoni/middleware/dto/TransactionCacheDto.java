package com.papaymoni.middleware.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionCacheDto implements Serializable {
    private Long id;
    private Long userId;
    private String userName;  // For notification
    private String userFirstName;  // For notification
    private String userEmail;  // For notification
    private Long orderId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private String status;
    private String externalReference;
    private Long virtualAccountId;
    private String virtualAccountNumber;
    private String paymentMethod;
    private String paymentDetails;
    private String receiptUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}