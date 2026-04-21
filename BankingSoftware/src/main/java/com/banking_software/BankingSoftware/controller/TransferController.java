package com.banking_software.BankingSoftware.controller;

import com.banking_software.BankingSoftware.dto.InboundTransferRequest;
import com.banking_software.BankingSoftware.dto.InterBankTransferRequest;
import com.banking_software.BankingSoftware.dto.IntraBankTransferRequest;
import com.banking_software.BankingSoftware.dto.TransferResponse;
import com.banking_software.BankingSoftware.entity.Transaction;
import com.banking_software.BankingSoftware.service.IdempotencyService;
import com.banking_software.BankingSoftware.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/intra")
    public TransferResponse intra(@RequestHeader("X-User-Id") Long userId,
                                  @RequestHeader("Idempotency-Key") String idemKey,
                                  @Valid @RequestBody IntraBankTransferRequest req) {
        return idempotencyService.execute(idemKey, "transfers.intra", userId,
                TransferResponse.class,
                () -> transferService.intraBankTransfer(userId, req));
    }

    @PostMapping("/inter")
    public TransferResponse inter(@RequestHeader("X-User-Id") Long userId,
                                  @RequestHeader("Idempotency-Key") String idemKey,
                                  @Valid @RequestBody InterBankTransferRequest req) {
        return idempotencyService.execute(idemKey, "transfers.inter", userId,
                TransferResponse.class,
                () -> transferService.interBankTransfer(userId, req));
    }

    /**
     * Called by the payment rail when another bank pushes funds to one of our accounts.
     * Idempotent on the rail's externalRef (UTR/RRN).
     */
    @PostMapping("/inbound")
    public TransferResponse inbound(@Valid @RequestBody InboundTransferRequest req) {
        return transferService.inboundTransfer(req);
    }

    @GetMapping("/history/{accountNumber}")
    public List<Transaction> history(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable String accountNumber) {
        return transferService.history(userId, accountNumber);
    }
}
