package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class PasswordResetDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestDto {
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenValidationDto {
        @NotBlank(message = "Token is required")
        private String token;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetDto {
        @NotBlank(message = "Token is required")
        private String token;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        private String password;

        @NotBlank(message = "Confirm password is required")
        private String confirmPassword;
    }
}
