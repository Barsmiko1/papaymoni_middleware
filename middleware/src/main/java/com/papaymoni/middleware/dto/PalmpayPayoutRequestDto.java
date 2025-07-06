package com.papaymoni.middleware.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PalmpayPayoutRequestDto {
    private Long requestTime;
    private String version;
    private String nonceStr;
    private String orderId;
    private String payeeName;
    private String payeeBankCode;
    private String payeeBankAccNo;
    private String payeePhoneNo;
    private BigDecimal amount;
    private String currency;
    private String notifyUrl;
    private String remark;
}
