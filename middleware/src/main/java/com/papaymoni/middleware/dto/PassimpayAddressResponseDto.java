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
public class PassimpayAddressResponseDto {

    @JsonProperty("result")
    private Integer result;

    @JsonProperty("address")
    private String address;

    @JsonProperty("destinationTag")
    private Integer destinationTag;

    private String error;
    private String Message;

}
