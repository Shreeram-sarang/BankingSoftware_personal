package com.banking_software.BankingSoftware.external;

import com.banking_software.BankingSoftware.entity.SettlementBatch;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock clearing-house (RBI / NPCI) adapter. Submits a net settlement batch
 * and returns a clearing-house reference number.
 */
@Component
public class ClearingHouseAdapter {

    @Data
    @AllArgsConstructor
    public static class Response {
        private boolean accepted;
        private String clearingHouseRef;
        private String failureReason;
    }

    public Response submit(SettlementBatch batch) {
        String ref = "CH-" + batch.getChannel() + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return new Response(true, ref, null);
    }
}
