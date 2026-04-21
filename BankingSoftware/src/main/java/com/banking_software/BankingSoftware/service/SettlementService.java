package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.external.ClearingHouseAdapter;
import com.banking_software.BankingSoftware.repository.InterBankTransferRepository;
import com.banking_software.BankingSoftware.repository.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * End-of-day settlement job.
 *
 * For every (counterparty bank, channel) pair that had eligible
 * inter-bank transfers on the given day, we:
 *   1. Group those transfers, sum them (net = outgoing - incoming).
 *      Today we only originate outgoing, so incoming is always 0.
 *   2. Upsert a SettlementBatch row for (date, bank, channel).
 *   3. Submit the batch to the clearing house (mocked).
 *   4. On ACK, post a single SETTLEMENT entry to our NOSTRO account and
 *      mark every child transfer SETTLED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final InterBankTransferRepository transferRepo;
    private final SettlementBatchRepository batchRepo;
    private final AccountService accountService;
    private final HouseAccountService houseAccountService;
    private final ClearingHouseAdapter clearingHouse;

    /** Fires at 23:30 every day. Disabled in tests where we call runForDate directly. */
    @Scheduled(cron = "0 30 23 * * *")
    @SchedulerLock(name = "settlementEndOfDay", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void runEndOfDay() {
        runForDate(LocalDate.now());
    }

    @Transactional
    public List<SettlementBatch> runForDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<TransferStatus> eligible = List.of(
                TransferStatus.ACKNOWLEDGED, TransferStatus.CLEARED);

        List<InterBankTransfer> transfers =
                transferRepo.findEligibleForSettlement(eligible, from, to);

        // Group by (bankId, channel)
        Map<GroupKey, List<InterBankTransfer>> grouped = new LinkedHashMap<>();
        for (InterBankTransfer t : transfers) {
            grouped.computeIfAbsent(
                    new GroupKey(t.getBeneficiaryBank().getId(), t.getChannel()),
                    k -> new ArrayList<>()).add(t);
        }

        List<SettlementBatch> result = new ArrayList<>();
        for (Map.Entry<GroupKey, List<InterBankTransfer>> e : grouped.entrySet()) {
            result.add(settleGroup(date, e.getValue()));
        }
        log.info("EOD settlement for {}: {} batches closed", date, result.size());
        return result;
    }

    private SettlementBatch settleGroup(LocalDate date, List<InterBankTransfer> transfers) {
        InterBankTransfer sample = transfers.get(0);
        Bank bank = sample.getBeneficiaryBank();
        PaymentChannel channel = sample.getChannel();

        SettlementBatch batch = batchRepo
                .findBySettlementDateAndCounterpartyBankIdAndChannel(date, bank.getId(), channel)
                .orElseGet(() -> {
                    SettlementBatch b = new SettlementBatch();
                    b.setSettlementDate(date);
                    b.setCounterpartyBank(bank);
                    b.setChannel(channel);
                    return b;
                });

        BigDecimal outgoing = transfers.stream()
                .filter(t -> t.getFlow() == TransferFlow.OUTGOING)
                .map(InterBankTransfer::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal incoming = transfers.stream()
                .filter(t -> t.getFlow() == TransferFlow.INCOMING)
                .map(InterBankTransfer::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        batch.setTotalOutgoing(outgoing);
        batch.setTotalIncoming(incoming);
        batch.setNetAmount(outgoing.subtract(incoming));
        batch.setTransactionCount(transfers.size());
        batch.setStatus(SettlementStatus.CLOSED);
        batch.setClosedAt(LocalDateTime.now());
        batchRepo.save(batch);

        ClearingHouseAdapter.Response chr = clearingHouse.submit(batch);
        if (!chr.isAccepted()) {
            batch.setStatus(SettlementStatus.FAILED);
            return batchRepo.save(batch);
        }

        batch.setClearingHouseRef(chr.getClearingHouseRef());
        batch.setStatus(SettlementStatus.SETTLED);
        batch.setSettledAt(LocalDateTime.now());

        // Post the net against NOSTRO. Positive net = we owe (debit);
        // negative net = we receive (credit).
        int sign = batch.getNetAmount().signum();
        if (sign != 0) {
            Account nostro = houseAccountService.getOrCreateNostro();
            String ref = "SETTLE-" + batch.getSettlementDate() + "-"
                    + bank.getBankCode() + "-" + channel;
            BigDecimal abs = batch.getNetAmount().abs();
            if (sign > 0) {
                accountService.postHouseDebit(nostro, abs, TransactionType.SETTLEMENT,
                        "Net settlement to " + bank.getName() + " via " + channel,
                        channel, ref);
            } else {
                accountService.postCredit(nostro, abs, TransactionType.SETTLEMENT,
                        "Net settlement from " + bank.getName() + " via " + channel,
                        channel, ref);
            }
        }

        // Mark every child transfer as SETTLED and link to batch.
        for (InterBankTransfer t : transfers) {
            t.setStatus(TransferStatus.SETTLED);
            t.setSettledAt(LocalDateTime.now());
        }
        transferRepo.saveAll(transfers);

        return batchRepo.save(batch);
    }

    private record GroupKey(Long bankId, PaymentChannel channel) {}
}
