package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassimpayWithdrawalResponseDto {
    private Integer result;
    private Integer paymentId;
    private String addressTo;
    private String amount;
    private String transactionId;
    private String message;
}
