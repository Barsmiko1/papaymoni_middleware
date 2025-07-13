package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InternalTransferResponseDto {
    private Long transactionId;
    private String senderUsername;
    private String recipientUsername;
    private String recipientFullName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private BigDecimal fee;
}