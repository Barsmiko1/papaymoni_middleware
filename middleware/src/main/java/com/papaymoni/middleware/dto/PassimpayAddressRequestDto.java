package com.papaymoni.middleware.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassimpayAddressRequestDto {

    @JsonProperty("platformId")
    private Integer platformId;

    @JsonProperty("paymentId")
    private Integer paymentId;

    @JsonProperty("orderId")
    private String orderId;
}
