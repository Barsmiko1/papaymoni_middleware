package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class EmailVerificationDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailVerificationRequestDto {
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerificationRequestDto {
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        private String email;

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
        private String otp;
    }
}