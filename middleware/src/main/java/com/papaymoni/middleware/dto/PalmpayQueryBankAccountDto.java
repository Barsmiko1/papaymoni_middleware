package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class PalmpayQueryBankAccountDto {
    private Long requestTime;
    private String version;
    private String nonceStr;
    private String bankCode;
    private String bankAccNo;
}
