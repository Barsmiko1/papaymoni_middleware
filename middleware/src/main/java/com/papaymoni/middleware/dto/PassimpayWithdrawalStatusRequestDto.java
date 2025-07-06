package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassimpayWithdrawalStatusRequestDto {
    private Integer platformId;
    private String transactionId;
}
