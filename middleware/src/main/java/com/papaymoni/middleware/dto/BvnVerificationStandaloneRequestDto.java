package com.papaymoni.middleware.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.LocalDate;

@Data
public class BvnVerificationStandaloneRequestDto {
    @NotBlank(message = "BVN is required")
    @Pattern(regexp = "\\d{11}", message = "BVN must be 11 digits")
    private String bvn;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Gender is required")
    private String gender;
}

