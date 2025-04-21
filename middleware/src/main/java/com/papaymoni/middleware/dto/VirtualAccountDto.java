//package com.papaymoni.middleware.dto;
//
//import lombok.Data;
//
//import javax.validation.constraints.NotBlank;
//import java.math.BigDecimal;
//
//@Data
//public class VirtualAccountDto {
//    private Long id;
//    private String accountNumber;
//    private String bankCode;
//    private String bankName;
//    private String accountName;
//
//    @NotBlank(message = "Currency is required")
//    private String currency;
//
//    private BigDecimal balance;
//    private boolean active;
//}

package com.papaymoni.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * DTO for virtual account operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountDto {

    @NotBlank(message = "Currency is required")
    private String currency;
}
