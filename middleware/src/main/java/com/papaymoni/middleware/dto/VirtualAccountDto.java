package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * DTO for virtual account operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountDto {

    @NotBlank(message = "Currency is required")
    private String currency;
}
