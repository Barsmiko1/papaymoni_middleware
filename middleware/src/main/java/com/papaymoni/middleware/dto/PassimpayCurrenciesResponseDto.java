package com.papaymoni.middleware.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassimpayCurrenciesResponseDto {

    @JsonProperty("result")
    private Integer result;

    @JsonProperty("list")
    private List<PassimpayCurrencyDto> list;
}
