package com.papaymoni.middleware.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralEvent implements Serializable {
    private Long referrerId;
    private Long referredUserId;
    private String currency;
    private BigDecimal transactionAmount;
    private BigDecimal bonusAmount;
    private String eventType; // BONUS_EARNED, MILESTONE_REACHED
}
