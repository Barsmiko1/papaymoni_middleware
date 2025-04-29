package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BvnVerificationResultDto {
    private boolean verified;
    private boolean firstNameMatch;
    private boolean lastNameMatch;
    private boolean dateOfBirthMatch;
    private boolean genderMatch;
    private String message;
    private String responseCode;
}