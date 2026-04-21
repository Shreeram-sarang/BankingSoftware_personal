package com.banking_software.BankingSoftware.external;

import com.banking_software.BankingSoftware.entity.InterBankTransfer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock adapter representing the external payment rail (NEFT/RTGS/IMPS/UPI).
 * Real implementations would format ISO 20022 / NPCI messages and call the rail.
 */
@Component
public class PaymentRailAdapter {

    @Data
    @AllArgsConstructor
    public static class RailResponse {
        private boolean accepted;
        private String externalRef;   // UTR / RRN
        private String failureReason;
    }

    public RailResponse send(InterBankTransfer transfer) {
        // Simulate: fail if beneficiary account looks invalid (<6 chars)
        if (transfer.getBeneficiaryAccountNumber() == null
                || transfer.getBeneficiaryAccountNumber().length() < 6) {
            return new RailResponse(false, null, "Invalid beneficiary account");
        }
        String utr = transfer.getChannel().name() + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return new RailResponse(true, utr, null);
    }
}
