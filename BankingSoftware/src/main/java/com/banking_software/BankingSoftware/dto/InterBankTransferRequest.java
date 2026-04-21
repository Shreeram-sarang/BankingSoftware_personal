package com.banking_software.BankingSoftware.dto;

import com.banking_software.BankingSoftware.entity.PaymentChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InterBankTransferRequest {
    @NotBlank
    private String fromAccountNumber;

    @NotBlank
    private String beneficiaryBankCode;

    @NotBlank
    private String beneficiaryAccountNumber;

    @NotBlank
    private String beneficiaryName;

    @NotBlank
    private String beneficiaryIfsc;

    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private PaymentChannel channel;

    private String remarks;
}
