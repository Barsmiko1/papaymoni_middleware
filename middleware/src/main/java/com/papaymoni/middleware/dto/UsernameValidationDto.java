package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsernameValidationDto {
    private boolean valid;
    private String fullName;
    private String message;
}


