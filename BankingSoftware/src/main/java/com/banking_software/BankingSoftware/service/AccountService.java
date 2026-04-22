package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.repository.TransactionRepository;
import com.banking_software.BankingSoftware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String IFSC_SELF = "MYBK0000001";
    private static final String BRANCH_SELF = "Main Branch";

    private final AccountRepository accountRepo;
    private final UserRepository userRepo;
    private final TransactionRepository txnRepo;

    @Transactional
    public Account create(CreateAccountRequest req) {
        User user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new BankingException("USER_NOT_FOUND", "User not found"));

        if (user.getKycStatus() != KycStatus.VERIFIED) {
            // For bootstrapping, auto-verify. In real world: block until KYC done.
            user.setKycStatus(KycStatus.VERIFIED);
        }

        Account a = new Account();
        a.setAccountNumber(generateAccountNumber());
        a.setUser(user);
        a.setType(req.getType());
        a.setStatus(AccountStatus.ACTIVE);
        a.setIfsc(IFSC_SELF);
        a.setBranch(BRANCH_SELF);
        a.setBalance(BigDecimal.ZERO);
        a.setAvailableBalance(BigDecimal.ZERO);
        Account saved = accountRepo.save(a);

        BigDecimal deposit = req.getInitialDeposit();
        if (deposit != null && deposit.signum() > 0) {
            postCredit(saved, deposit, TransactionType.DEPOSIT, "Initial deposit",
                    PaymentChannel.INTERNAL, UUID.randomUUID().toString());
        }
        return saved;
    }

    /** Posts a credit ledger entry and updates balance. Caller must be @Transactional. */
    public Transaction postCredit(Account acc, BigDecimal amount, TransactionType type,
                                  String description, PaymentChannel channel, String ref) {
        requirePositive(amount);
        acc.setBalance(acc.getBalance().add(amount));
        acc.setAvailableBalance(acc.getAvailableBalance().add(amount));
        accountRepo.save(acc);

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAccount(acc);
        t.setDirection(TransactionDirection.CREDIT);
        t.setType(type);
        t.setStatus(TransactionStatus.POSTED);
        t.setAmount(amount);
        t.setBalanceAfter(acc.getBalance());
        t.setChannel(channel);
        t.setDescription(description);
        t.setValueDate(LocalDate.now());
        return txnRepo.save(t);
    }

    /** Posts a debit ledger entry. Caller must @Transactional and have locked the account. */
    public Transaction postDebit(Account acc, BigDecimal amount, TransactionType type,
                                 String description, PaymentChannel channel, String ref) {
        requirePositive(amount);
        if (acc.getAvailableBalance().compareTo(amount) < 0) {
            throw new BankingException("INSUFFICIENT_FUNDS",
                    "Available balance " + acc.getAvailableBalance() + " < " + amount);
        }
        acc.setBalance(acc.getBalance().subtract(amount));
        acc.setAvailableBalance(acc.getAvailableBalance().subtract(amount));
        accountRepo.save(acc);

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAccount(acc);
        t.setDirection(TransactionDirection.DEBIT);
        t.setType(type);
        t.setStatus(TransactionStatus.POSTED);
        t.setAmount(amount);
        t.setBalanceAfter(acc.getBalance());
        t.setChannel(channel);
        t.setDescription(description);
        t.setValueDate(LocalDate.now());
        return txnRepo.save(t);
    }

    /** Debit that ignores the availableBalance guard (house/NOSTRO accounts). */
    public Transaction postHouseDebit(Account acc, BigDecimal amount, TransactionType type,
                                      String description, PaymentChannel channel, String ref) {
        requirePositive(amount);
        acc.setBalance(acc.getBalance().subtract(amount));
        acc.setAvailableBalance(acc.getAvailableBalance().subtract(amount));
        accountRepo.save(acc);

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAccount(acc);
        t.setDirection(TransactionDirection.DEBIT);
        t.setType(type);
        t.setStatus(TransactionStatus.POSTED);
        t.setAmount(amount);
        t.setBalanceAfter(acc.getBalance());
        t.setChannel(channel);
        t.setDescription(description);
        t.setValueDate(LocalDate.now());
        return txnRepo.save(t);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankingException("INVALID_AMOUNT",
                    "Amount must be strictly positive (got " + amount + ")");
        }
    }

    private String generateAccountNumber() {
        String n;
        do {
            n = String.valueOf(1_000_000_000_000L + ThreadLocalRandom.current().nextLong(8_999_999_999_999L));
        } while (accountRepo.existsByAccountNumber(n));
        return n;
    }
}
