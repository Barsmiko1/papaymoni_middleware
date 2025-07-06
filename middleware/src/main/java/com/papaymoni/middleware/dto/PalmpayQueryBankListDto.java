package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class PalmpayQueryBankListDto {
    private Long requestTime;
    private String version;
    private String nonceStr;
    private Integer businessType;
}
