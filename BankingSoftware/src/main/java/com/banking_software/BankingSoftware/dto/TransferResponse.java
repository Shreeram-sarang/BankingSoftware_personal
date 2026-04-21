package com.banking_software.BankingSoftware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TransferResponse {
    private String transactionRef;
    private String status;
    private BigDecimal amount;
    private BigDecimal fromBalanceAfter;
    private String externalRef;
}
