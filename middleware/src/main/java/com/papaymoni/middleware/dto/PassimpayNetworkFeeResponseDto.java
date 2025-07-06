package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassimpayNetworkFeeResponseDto {
    private Integer result;
    private Integer paymentId;
    private String amountFull;
    private String amount;
    private String feeNetwork;
    private String feeService;
    private String message;
}
