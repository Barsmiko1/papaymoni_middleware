package com.papaymoni.middleware.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class TransactionDto {
    private Long id;

    @NotBlank(message = "Transaction type is required")
    private String transactionType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private BigDecimal fee;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String status;
    private String externalReference;
    private Long virtualAccountId;
    private String paymentMethod;
    private String paymentDetails;
}
