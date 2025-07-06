package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class BankAccountQueryDto {
    private String bankCode;
    private String bankAccNo;
    private String accountName;
    private String status;
}
