package com.papaymoni.middleware.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class OrderDto {
    private String id;

    @NotBlank(message = "Item ID is required")
    private String bybitItemId;

    @NotBlank(message = "Token ID is required")
    private String tokenId;

    @NotBlank(message = "Currency ID is required")
    private String currencyId;

    @NotNull(message = "Side is required")
    private Integer side;

    private String orderType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    private String targetUserId;
    private String targetNickName;
}
