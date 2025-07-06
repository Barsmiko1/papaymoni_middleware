package com.papaymoni.middleware.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PalmpayQueryTransactionDto {
    private Long requestTime;
    private String version;
    private String nonceStr;
    private String orderId;
    private String orderNo;
}
