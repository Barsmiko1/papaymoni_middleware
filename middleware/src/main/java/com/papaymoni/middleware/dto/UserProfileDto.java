package com.papaymoni.middleware.dto;

import com.papaymoni.middleware.model.VirtualAccount;
import lombok.Data;

import java.util.List;

@Data
public class UserProfileDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String referralCode;
    private VirtualAccount virtualAccount;
}