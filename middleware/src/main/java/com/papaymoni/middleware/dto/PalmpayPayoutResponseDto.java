package com.papaymoni.middleware.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PalmpayPayoutResponseDto {
    private String respCode;
    private String respMsg;
    private PayoutData data;

    @Data
    public static class PayoutData {
        private BigDecimal amount;
        private String orderNo;
        private String orderId;
        private FeeData fee;
        private Integer status;
        private Integer orderStatus;
        private String sessionId;
        private String message;
        private Long createTime;
        private Long completedTime;
    }

    @Data
    public static class FeeData {
        private BigDecimal fee;
        private BigDecimal vat;
    }

    public boolean isSuccess() {
        return "00000000".equals(respCode);
    }
}
