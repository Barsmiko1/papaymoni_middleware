package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PalmpayPayoutWebhookDto {
    private String orderId;
    private String orderNo;
    private String appId;
    private String transType;
    private Integer status;
    private BigDecimal amount;
    private Integer orderStatus;
    private String sessionId;
    private Long completeTime;
    private String sign;
}
