//package com.papaymoni.middleware.dto;
//
//import lombok.Data;
//
//import javax.validation.constraints.NotBlank;
//
//@Data
//public class BybitCredentialsDto {
//    @NotBlank(message = "API Key is required")
//    private String apiKey;
//
//    @NotBlank(message = "API Secret is required")
//    private String apiSecret;
//}


package com.papaymoni.middleware.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * DTO for transferring Bybit API credentials
 */
@Data
@NoArgsConstructor
public class BybitCredentialsDto {

    @NotBlank(message = "API key is required")
    private String apiKey;

    @NotBlank(message = "API secret is required")
    private String apiSecret;
}
