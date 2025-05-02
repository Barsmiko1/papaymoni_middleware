package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class PalmpayPayinWebhookDto {
    private String orderNo;             // PalmPay platform order No
    private Integer orderStatus;        // Virtual Account order status
    private Long createdTime;           // Order create time
    private Long updateTime;            // Order update time
    private String currency;            // Currency (NGN)
    private Long orderAmount;           // The initiated amount in cents
    private String reference;           // Payer reference
    private String payerAccountNo;      // Payer account number
    private String payerAccountName;    // Payer account name
    private String payerBankName;       // Payer bank name
    private String virtualAccountNo;    // Virtual account number
    private String virtualAccountName;  // Virtual account name
    private String accountReference;    // Reference field for the virtual account
    private String sessionId;           // Session ID for the transaction
    private String sign;                // Signature for verification
    private String appId;                // appId
}