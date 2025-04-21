package com.papaymoni.middleware.dto;

import lombok.Data;

@Data
public class AdSearchRequest {
    private String tokenId;      // Cryptocurrency token ID (e.g., USDT)
    private String currencyId;   // Fiat currency ID (e.g., NGN)
    private String side;         // Trade side: "0" for buy, "1" for sell
}