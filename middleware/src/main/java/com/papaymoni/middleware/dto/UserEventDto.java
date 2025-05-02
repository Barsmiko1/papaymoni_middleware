package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEventDto implements Serializable {
    private Long userId;
    private String username;
    private String email;
    private String phoneNumber;
    private String referredBy;

    // Add only the fields needed for event processing
    // This avoids serializing the entire User entity
}