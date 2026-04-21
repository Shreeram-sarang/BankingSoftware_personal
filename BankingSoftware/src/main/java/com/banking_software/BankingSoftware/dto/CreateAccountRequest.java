package com.banking_software.BankingSoftware.dto;

import com.banking_software.BankingSoftware.entity.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountRequest {
    @NotNull
    private Long userId;

    @NotNull
    private AccountType type;

    @NotNull
    @DecimalMin(value = "0.00", message = "initial deposit cannot be negative")
    private BigDecimal initialDeposit = BigDecimal.ZERO;
}
