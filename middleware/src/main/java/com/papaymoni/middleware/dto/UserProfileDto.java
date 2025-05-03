// Update UserProfileDto.java
package com.papaymoni.middleware.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String referralCode;
    private String VirtualAccount;


    // VirtualAccountDto
    private List<VirtualAccountDto> virtualAccounts;

    // Add helper class for simplified virtual account data
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VirtualAccountDto {
        private Long id;
        private String accountNumber;
        private String bankCode;
        private String bankName;
        private String accountName;
        private String currency;
        private boolean active;
    }
}