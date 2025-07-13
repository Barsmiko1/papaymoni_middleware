package com.papaymoni.middleware.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class BvnVerificationRequestDto {
    @NotBlank(message = "BVN is required")
    @Pattern(regexp = "\\d{11}", message = "BVN must be 11 digits")
    private String bvn;
}
