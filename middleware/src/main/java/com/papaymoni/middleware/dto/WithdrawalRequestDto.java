package com.papaymoni.middleware.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class WithdrawalRequestDto {
    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    private String bankCode;

    private String bankName;

    private String accountName;

    private String phoneNumber;

    private String remark;
}
