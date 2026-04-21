package com.banking_software.BankingSoftware.dto;

import com.banking_software.BankingSoftware.entity.PaymentChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload received from a payment rail (NEFT/IMPS/UPI/RTGS) when another
 * bank sends money to one of our customers.
 */
@Data
public class InboundTransferRequest {
    @NotBlank
    private String originatorBankCode;

    @NotBlank
    private String originatorAccountNumber;

    @NotBlank
    private String originatorName;

    @NotBlank
    private String destinationAccountNumber;

    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private PaymentChannel channel;

    /** UTR/RRN from the rail. Used as the idempotency anchor. */
    @NotBlank
    private String externalRef;

    private String remarks;
}
