package com.banking_software.BankingSoftware.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** One line item from a rail's end-of-day statement file. */
@Data
public class RailStatementEntry {
    @NotBlank
    private String externalRef;

    @NotNull
    private BigDecimal amount;

    /** Rail's view of the outcome: SETTLED, FAILED, RETURNED, etc. */
    @NotBlank
    private String status;
}
