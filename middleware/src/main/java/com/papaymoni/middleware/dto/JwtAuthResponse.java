package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtAuthResponse {
    private String accessToken;
    private String tokenType;

    public JwtAuthResponse(String accessToken) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
    }
}
