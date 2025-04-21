package com.papaymoni.middleware.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent implements Serializable {
    private Long transactionId;
    private Long userId;
    private String orderId;
}
