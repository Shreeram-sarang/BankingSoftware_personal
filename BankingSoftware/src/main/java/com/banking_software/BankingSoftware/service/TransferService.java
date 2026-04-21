package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.dto.InboundTransferRequest;
import com.banking_software.BankingSoftware.dto.InterBankTransferRequest;
import com.banking_software.BankingSoftware.dto.IntraBankTransferRequest;
import com.banking_software.BankingSoftware.dto.TransferResponse;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.external.PaymentRailAdapter;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.repository.BankRepository;
import com.banking_software.BankingSoftware.repository.InterBankTransferRepository;
import com.banking_software.BankingSoftware.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepo;
    private final BankRepository bankRepo;
    private final TransactionRepository txnRepo;
    private final InterBankTransferRepository transferRepo;
    private final AccountService accountService;
    private final LimitService limitService;
    private final PaymentRailAdapter railAdapter;

    // ---------- Intra-bank ----------
    @Transactional
    public TransferResponse intraBankTransfer(Long callerUserId, IntraBankTransferRequest req) {
        if (req.getFromAccountNumber().equals(req.getToAccountNumber())) {
            throw new BankingException("SAME_ACCOUNT", "Source and destination cannot be same");
        }

        // Lock both accounts in a deterministic order to avoid deadlocks.
        String first = req.getFromAccountNumber();
        String second = req.getToAccountNumber();
        boolean swapped = first.compareTo(second) > 0;
        String lock1 = swapped ? second : first;
        String lock2 = swapped ? first : second;

        Account a1 = accountRepo.findByAccountNumberForUpdate(lock1)
                .orElseThrow(() -> new BankingException("ACCOUNT_NOT_FOUND",
                        "Account " + lock1 + " not found"));
        Account a2 = accountRepo.findByAccountNumberForUpdate(lock2)
                .orElseThrow(() -> new BankingException("ACCOUNT_NOT_FOUND",
                        "Account " + lock2 + " not found"));

        Account from = a1.getAccountNumber().equals(req.getFromAccountNumber()) ? a1 : a2;
        Account to = from == a1 ? a2 : a1;

        assertOwnership(from, callerUserId);
        validateActive(from);
        validateActive(to);

        limitService.checkIntra(from.getId(), req.getAmount());

        String ref = "INTRA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        Transaction debit = accountService.postDebit(from, req.getAmount(),
                TransactionType.INTRA_BANK_TRANSFER,
                "Transfer to " + to.getAccountNumber(),
                PaymentChannel.INTERNAL, ref);
        debit.setCounterpartyAccountNumber(to.getAccountNumber());
        txnRepo.save(debit);

        Transaction credit = accountService.postCredit(to, req.getAmount(),
                TransactionType.INTRA_BANK_TRANSFER,
                "Transfer from " + from.getAccountNumber(),
                PaymentChannel.INTERNAL, ref);
        credit.setCounterpartyAccountNumber(from.getAccountNumber());
        txnRepo.save(credit);

        return new TransferResponse(ref, "POSTED", req.getAmount(), from.getBalance(), null);
    }

    // ---------- Inter-bank ----------
    @Transactional
    public TransferResponse interBankTransfer(Long callerUserId, InterBankTransferRequest req) {
        Account from = accountRepo.findByAccountNumberForUpdate(req.getFromAccountNumber())
                .orElseThrow(() -> new BankingException("ACCOUNT_NOT_FOUND",
                        "Account " + req.getFromAccountNumber() + " not found"));
        assertOwnership(from, callerUserId);
        validateActive(from);

        Bank beneficiaryBank = bankRepo.findByBankCode(req.getBeneficiaryBankCode())
                .orElseThrow(() -> new BankingException("BANK_NOT_FOUND",
                        "Bank " + req.getBeneficiaryBankCode() + " not registered"));
        if (beneficiaryBank.isSelf()) {
            throw new BankingException("USE_INTRA",
                    "Beneficiary is same bank — use intra-bank transfer");
        }

        limitService.checkInter(from.getId(), req.getAmount(), req.getChannel());

        String ref = "INTER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        InterBankTransfer ibt = new InterBankTransfer();
        ibt.setTransactionRef(ref);
        ibt.setSourceAccount(from);
        ibt.setBeneficiaryBank(beneficiaryBank);
        ibt.setBeneficiaryAccountNumber(req.getBeneficiaryAccountNumber());
        ibt.setBeneficiaryName(req.getBeneficiaryName());
        ibt.setBeneficiaryIfsc(req.getBeneficiaryIfsc());
        ibt.setAmount(req.getAmount());
        ibt.setChannel(req.getChannel());
        ibt.setRemarks(req.getRemarks());
        ibt.setStatus(TransferStatus.VALIDATED);
        transferRepo.save(ibt);

        // Debit customer now; hold is implicit because we moved balance.
        Transaction debit = accountService.postDebit(from, req.getAmount(),
                TransactionType.INTER_BANK_TRANSFER,
                "To " + req.getBeneficiaryName() + " @ " + beneficiaryBank.getName(),
                req.getChannel(), ref);
        debit.setCounterpartyBank(beneficiaryBank);
        debit.setCounterpartyAccountNumber(req.getBeneficiaryAccountNumber());
        debit.setCounterpartyName(req.getBeneficiaryName());
        debit.setCounterpartyIfsc(req.getBeneficiaryIfsc());
        debit.setStatus(TransactionStatus.PENDING); // becomes POSTED after rail ack
        txnRepo.save(debit);

        ibt.setStatus(TransferStatus.SENT_TO_RAIL);
        PaymentRailAdapter.RailResponse rr = railAdapter.send(ibt);

        if (!rr.isAccepted()) {
            // reverse the debit
            ibt.setStatus(TransferStatus.FAILED);
            ibt.setFailureReason(rr.getFailureReason());
            transferRepo.save(ibt);

            accountService.postCredit(from, req.getAmount(),
                    TransactionType.REVERSAL,
                    "Reversal: " + rr.getFailureReason(),
                    req.getChannel(), ref);
            debit.setStatus(TransactionStatus.REVERSED);
            txnRepo.save(debit);

            throw new BankingException("RAIL_REJECTED", rr.getFailureReason());
        }

        ibt.setExternalRef(rr.getExternalRef());
        ibt.setStatus(TransferStatus.ACKNOWLEDGED);
        transferRepo.save(ibt);

        debit.setStatus(TransactionStatus.POSTED);
        debit.setExternalRef(rr.getExternalRef());
        txnRepo.save(debit);

        return new TransferResponse(ref, ibt.getStatus().name(),
                req.getAmount(), from.getBalance(), rr.getExternalRef());
    }

    // ---------- Inbound from another bank ----------
    @Transactional
    public TransferResponse inboundTransfer(InboundTransferRequest req) {
        // Rail-level idempotency: UTR must be processed only once.
        var existing = transferRepo.findByExternalRef(req.getExternalRef());
        if (existing.isPresent()) {
            InterBankTransfer e = existing.get();
            return new TransferResponse(e.getTransactionRef(), e.getStatus().name(),
                    e.getAmount(), null, e.getExternalRef());
        }

        Account dest = accountRepo.findByAccountNumberForUpdate(req.getDestinationAccountNumber())
                .orElseThrow(() -> new BankingException("ACCOUNT_NOT_FOUND",
                        "Destination account " + req.getDestinationAccountNumber() + " not found"));
        validateActive(dest);

        Bank originatorBank = bankRepo.findByBankCode(req.getOriginatorBankCode())
                .orElseThrow(() -> new BankingException("BANK_NOT_FOUND",
                        "Originating bank " + req.getOriginatorBankCode() + " not registered"));
        if (originatorBank.isSelf()) {
            throw new BankingException("ORIGINATOR_IS_SELF",
                    "Originator bank is marked as self — inbound refused");
        }

        String ref = "INB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        InterBankTransfer ibt = new InterBankTransfer();
        ibt.setTransactionRef(ref);
        ibt.setFlow(TransferFlow.INCOMING);
        ibt.setBeneficiaryBank(originatorBank);          // the "other" bank
        ibt.setBeneficiaryAccountNumber(req.getOriginatorAccountNumber());
        ibt.setBeneficiaryName(req.getOriginatorName());
        ibt.setDestinationAccount(dest);
        ibt.setAmount(req.getAmount());
        ibt.setChannel(req.getChannel());
        ibt.setRemarks(req.getRemarks());
        ibt.setExternalRef(req.getExternalRef());
        ibt.setStatus(TransferStatus.ACKNOWLEDGED);
        transferRepo.save(ibt);

        Transaction credit = accountService.postCredit(dest, req.getAmount(),
                TransactionType.INTER_BANK_TRANSFER,
                "From " + req.getOriginatorName() + " @ " + originatorBank.getName(),
                req.getChannel(), ref);
        credit.setCounterpartyBank(originatorBank);
        credit.setCounterpartyAccountNumber(req.getOriginatorAccountNumber());
        credit.setCounterpartyName(req.getOriginatorName());
        credit.setExternalRef(req.getExternalRef());
        txnRepo.save(credit);

        return new TransferResponse(ref, ibt.getStatus().name(),
                req.getAmount(), dest.getBalance(), req.getExternalRef());
    }

    public List<Transaction> history(Long callerUserId, String accountNumber) {
        Account a = accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankingException("ACCOUNT_NOT_FOUND",
                        "Account " + accountNumber + " not found"));
        assertOwnership(a, callerUserId);
        return txnRepo.findByAccountIdOrderByPostedAtDesc(a.getId());
    }

    private void assertOwnership(Account a, Long callerUserId) {
        if (callerUserId == null || !a.getUser().getId().equals(callerUserId)) {
            throw new BankingException("FORBIDDEN",
                    "Account " + a.getAccountNumber() + " does not belong to caller");
        }
    }

    private void validateActive(Account a) {
        if (a.getStatus() != AccountStatus.ACTIVE) {
            throw new BankingException("ACCOUNT_NOT_ACTIVE",
                    "Account " + a.getAccountNumber() + " is " + a.getStatus());
        }
    }

}
