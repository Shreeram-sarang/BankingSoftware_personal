package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.dto.RailStatementEntry;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.repository.InterBankTransferRepository;
import com.banking_software.BankingSoftware.repository.ReconciliationExceptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Compares the payment rail's daily statement against our InterBankTransfer rows.
 * Writes a ReconciliationException for every discrepancy:
 *   - missing on our side (rail says something we don't know about)
 *   - missing on their side (we think it cleared but rail didn't report it)
 *   - amount mismatch
 *   - status mismatch (e.g. rail reports FAILED but we have it ACKNOWLEDGED)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final InterBankTransferRepository transferRepo;
    private final ReconciliationExceptionRepository exceptionRepo;

    @Transactional
    public ReconciliationResult reconcile(LocalDate date, List<RailStatementEntry> entries) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        Map<String, InterBankTransfer> ours = new HashMap<>();
        for (InterBankTransfer t : transferRepo.findWithExternalRefInWindow(from, to)) {
            ours.put(t.getExternalRef(), t);
        }

        Set<String> seenExternal = new HashSet<>();
        List<ReconciliationException> exceptions = new ArrayList<>();
        int matched = 0;

        for (RailStatementEntry e : entries) {
            seenExternal.add(e.getExternalRef());
            InterBankTransfer t = ours.get(e.getExternalRef());
            if (t == null) {
                exceptions.add(build(date, ReconciliationExceptionType.MISSING_INTERNAL,
                        e.getExternalRef(), null, e.getAmount(), null, null, e.getStatus(),
                        "Rail reports a transfer we don't have"));
                continue;
            }
            boolean amtOk = t.getAmount().compareTo(e.getAmount()) == 0;
            boolean statusOk = isStatusConsistent(t.getStatus(), e.getStatus());

            if (!amtOk) {
                exceptions.add(build(date, ReconciliationExceptionType.AMOUNT_MISMATCH,
                        e.getExternalRef(), t, t.getAmount(), e.getAmount(),
                        t.getStatus().name(), e.getStatus(),
                        "Amount mismatch: internal " + t.getAmount() + " vs rail " + e.getAmount()));
            }
            if (!statusOk) {
                exceptions.add(build(date, ReconciliationExceptionType.STATUS_MISMATCH,
                        e.getExternalRef(), t, t.getAmount(), e.getAmount(),
                        t.getStatus().name(), e.getStatus(),
                        "Status mismatch: internal " + t.getStatus() + " vs rail " + e.getStatus()));
            }
            if (amtOk && statusOk) matched++;
        }

        for (Map.Entry<String, InterBankTransfer> me : ours.entrySet()) {
            if (!seenExternal.contains(me.getKey())) {
                InterBankTransfer t = me.getValue();
                exceptions.add(build(date, ReconciliationExceptionType.MISSING_EXTERNAL,
                        t.getExternalRef(), t, t.getAmount(), null,
                        t.getStatus().name(), null,
                        "We have a transfer the rail didn't report"));
            }
        }

        exceptionRepo.saveAll(exceptions);
        log.info("Reconciliation for {}: matched={}, exceptions={}, rail-entries={}, internal={}",
                date, matched, exceptions.size(), entries.size(), ours.size());

        return new ReconciliationResult(date, matched, exceptions.size(),
                entries.size(), ours.size(), exceptions);
    }

    /**
     * Our TransferStatus vs the rail's free-form status. We consider the
     * rail's SETTLED/CLEARED/SUCCESS consistent with ACKNOWLEDGED/SETTLED/CLEARED
     * on our side, and FAILED consistent with FAILED/RETURNED/REVERSED.
     */
    private boolean isStatusConsistent(TransferStatus internal, String rail) {
        String r = rail.trim().toUpperCase();
        Set<TransferStatus> ok = switch (r) {
            case "SETTLED", "CLEARED", "SUCCESS", "ACKNOWLEDGED" ->
                    Set.of(TransferStatus.ACKNOWLEDGED, TransferStatus.CLEARED, TransferStatus.SETTLED);
            case "FAILED", "RETURNED", "REVERSED", "REJECTED" ->
                    Set.of(TransferStatus.FAILED, TransferStatus.RETURNED, TransferStatus.REVERSED);
            default -> Set.of();
        };
        return ok.contains(internal);
    }

    private ReconciliationException build(LocalDate date, ReconciliationExceptionType type,
                                          String extRef, InterBankTransfer t,
                                          BigDecimal expected, BigDecimal actual,
                                          String expStatus, String actStatus, String reason) {
        ReconciliationException e = new ReconciliationException();
        e.setStatementDate(date);
        e.setType(type);
        e.setExternalRef(extRef);
        e.setInterBankTransfer(t);
        e.setExpectedAmount(expected);
        e.setActualAmount(actual);
        e.setExpectedStatus(expStatus);
        e.setActualStatus(actStatus);
        e.setReason(reason);
        return e;
    }

    public record ReconciliationResult(
            LocalDate date,
            int matched,
            int exceptionCount,
            int railEntryCount,
            int internalCount,
            List<ReconciliationException> exceptions) {}
}
