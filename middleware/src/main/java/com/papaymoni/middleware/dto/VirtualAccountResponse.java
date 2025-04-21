package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class VirtualAccountResponse {
    private String accountNumber;
    private String bankCode;
    private String bankName;
    private String accountName;
}
