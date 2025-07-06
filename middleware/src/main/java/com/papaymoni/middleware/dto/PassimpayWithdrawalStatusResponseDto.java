package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassimpayWithdrawalStatusResponseDto {
    private Integer result;
    private Integer approve; // 0: Pending, 1: Successful, 2: Error
    private Integer paymentId;
    private String addressTo;
    private String amount;
    private String txhash;
    private Integer confirmations;
    private String message;
}
