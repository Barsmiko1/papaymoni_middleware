package com.papaymoni.middleware.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VirtualAccountResponseDto {
    private String accountNumber;
    private String bankCode;
    private String bankName;
    private String accountName;
    private String currency;
    private BigDecimal balance;
    private boolean active;
    private String id;
    private boolean existingAccount;


    // Additional fields from Palmpay response
    private String status;
    private String identityType;
    private String licenseNumber;
    private String customerName;
    private String email;
    private String accountReference;

    // Method to create response from Palmpay data
    public static VirtualAccountResponseDto fromPalmpayResponse(Map<String, Object> data) {
        VirtualAccountResponseDto response = new VirtualAccountResponseDto();
        response.setAccountNumber((String) data.get("virtualAccountNo"));
        response.setBankName("BLOOMS MFB");
        response.setBankCode("090743"); // Default bank code for Palmpay
        response.setAccountName((String) data.get("virtualAccountName"));
        response.setStatus((String) data.get("status"));
        response.setIdentityType((String) data.get("identityType"));
        response.setLicenseNumber((String) data.get("licenseNumber"));
        response.setCustomerName((String) data.get("customerName"));
        response.setEmail((String) data.get("email"));
        response.setAccountReference((String) data.get("accountReference"));
        response.setBalance((BigDecimal) data.get("balance"));
        response.setExistingAccount(false); // Default to false
        return response;
    }
}