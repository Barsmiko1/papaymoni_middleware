package com.papaymoni.middleware.model.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    CREATED(0, "Created"),
    WAITING_FOR_PAYMENT(1, "Waiting for Payment"),
    PAID(2, "Paid"),
    COMPLETED(3, "Completed"),
    CANCELLED(4, "Cancelled"),
    DISPUTED(5, "Disputed");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid OrderStatus code: " + code);
    }
}
