package com.papaymoni.middleware.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserLoginDto {
    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    private String password;
}
