package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class CurrencyDto {
    private Long id;
    private String code;
    private String name;
    private String symbol;
    private Integer decimalPlaces;
    private Boolean active;
}
