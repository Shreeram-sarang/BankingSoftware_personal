package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.dto.InterBankTransferRequest;
import com.banking_software.BankingSoftware.dto.IntraBankTransferRequest;
import com.banking_software.BankingSoftware.dto.TransferResponse;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.repository.BankRepository;
import com.banking_software.BankingSoftware.repository.InterBankTransferRepository;
import com.banking_software.BankingSoftware.repository.TransactionRepository;
import com.banking_software.BankingSoftware.service.AccountService;
import com.banking_software.BankingSoftware.service.TransferService;
import com.banking_software.BankingSoftware.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BankingFlowTest {

    @Autowired UserService userService;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepo;
    @Autowired BankRepository bankRepo;
    @Autowired TransactionRepository txnRepo;
    @Autowired InterBankTransferRepository transferRepo;

    private User createUser(String email, String phone) {
        CreateUserRequest r = new CreateUserRequest();
        r.setFirstName("Test");
        r.setEmail(email);
        r.setPhone(phone);
        return userService.create(r);
    }

    private Account createAccount(Long userId, BigDecimal deposit) {
        CreateAccountRequest r = new CreateAccountRequest();
        r.setUserId(userId);
        r.setType(AccountType.SAVINGS);
        r.setInitialDeposit(deposit);
        return accountService.create(r);
    }

    private Bank registerBank(String code, String name, boolean self) {
        Bank b = new Bank();
        b.setBankCode(code);
        b.setName(name);
        b.setSelf(self);
        return bankRepo.save(b);
    }

    @Test
    void userAndAccountCreation_depositsPostCredit() {
        User u = createUser("a@x.com", "9000000001");
        Account a = createAccount(u.getId(), new BigDecimal("5000"));

        assertThat(a.getAccountNumber()).hasSize(13);
        assertThat(a.getBalance()).isEqualByComparingTo("5000");
        assertThat(a.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        List<Transaction> hist = txnRepo.findByAccountIdOrderByPostedAtDesc(a.getId());
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(hist.get(0).getType()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void intraBankTransfer_debitsAndCredits() {
        User u1 = createUser("b@x.com", "9000000002");
        User u2 = createUser("c@x.com", "9000000003");
        Account from = createAccount(u1.getId(), new BigDecimal("10000"));
        Account to = createAccount(u2.getId(), new BigDecimal("1000"));

        IntraBankTransferRequest req = new IntraBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setToAccountNumber(to.getAccountNumber());
        req.setAmount(new BigDecimal("2500"));

        TransferResponse resp = transferService.intraBankTransfer(u1.getId(), req);
        assertThat(resp.getStatus()).isEqualTo("POSTED");

        Account fromAfter = accountRepo.findById(from.getId()).orElseThrow();
        Account toAfter = accountRepo.findById(to.getId()).orElseThrow();
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("7500");
        assertThat(toAfter.getBalance()).isEqualByComparingTo("3500");

        List<Transaction> legs = txnRepo.findByTransactionRef(resp.getTransactionRef());
        assertThat(legs).hasSize(2);
    }

    @Test
    void intraBankTransfer_insufficientFunds_throws() {
        User u1 = createUser("d@x.com", "9000000004");
        User u2 = createUser("e@x.com", "9000000005");
        Account from = createAccount(u1.getId(), new BigDecimal("100"));
        Account to = createAccount(u2.getId(), BigDecimal.ZERO);

        IntraBankTransferRequest req = new IntraBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setToAccountNumber(to.getAccountNumber());
        req.setAmount(new BigDecimal("500"));

        assertThatThrownBy(() -> transferService.intraBankTransfer(u1.getId(), req))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Available balance");
    }

    @Test
    void intraBankTransfer_perTxnLimit_throws() {
        User u1 = createUser("f@x.com", "9000000006");
        User u2 = createUser("g@x.com", "9000000007");
        Account from = createAccount(u1.getId(), new BigDecimal("5000000"));
        Account to = createAccount(u2.getId(), BigDecimal.ZERO);

        IntraBankTransferRequest req = new IntraBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setToAccountNumber(to.getAccountNumber());
        req.setAmount(new BigDecimal("1500000")); // > 1,000,000 limit

        assertThatThrownBy(() -> transferService.intraBankTransfer(u1.getId(), req))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Intra-bank per-transaction limit");
    }

    @Test
    void interBankTransfer_neftHappyPath() {
        registerBank("MYBK", "My Bank", true);
        registerBank("HDFC", "HDFC Bank", false);

        User u = createUser("h@x.com", "9000000008");
        Account from = createAccount(u.getId(), new BigDecimal("20000"));

        InterBankTransferRequest req = new InterBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setBeneficiaryBankCode("HDFC");
        req.setBeneficiaryAccountNumber("1234567890");
        req.setBeneficiaryName("Raj");
        req.setBeneficiaryIfsc("HDFC0000123");
        req.setAmount(new BigDecimal("5000"));
        req.setChannel(PaymentChannel.NEFT);

        TransferResponse resp = transferService.interBankTransfer(u.getId(), req);

        assertThat(resp.getStatus()).isEqualTo(TransferStatus.ACKNOWLEDGED.name());
        assertThat(resp.getExternalRef()).startsWith("NEFT-");

        Account after = accountRepo.findById(from.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("15000");

        InterBankTransfer ibt = transferRepo.findByTransactionRef(resp.getTransactionRef()).orElseThrow();
        assertThat(ibt.getStatus()).isEqualTo(TransferStatus.ACKNOWLEDGED);
        assertThat(ibt.getExternalRef()).isNotBlank();
    }

    @Test
    void interBankTransfer_railRejects_reversesDebit() {
        registerBank("HDFC2", "HDFC Bank", false);

        User u = createUser("i@x.com", "9000000009");
        Account from = createAccount(u.getId(), new BigDecimal("10000"));

        InterBankTransferRequest req = new InterBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setBeneficiaryBankCode("HDFC2");
        req.setBeneficiaryAccountNumber("12345"); // < 6 chars -> adapter rejects
        req.setBeneficiaryName("X");
        req.setBeneficiaryIfsc("HDFC0000999");
        req.setAmount(new BigDecimal("1000"));
        req.setChannel(PaymentChannel.NEFT);

        assertThatThrownBy(() -> transferService.interBankTransfer(u.getId(), req))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Invalid beneficiary account");

        // Debit should have been reversed -> balance unchanged
        Account after = accountRepo.findById(from.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("10000");
    }

    @Test
    void interBankTransfer_rtgsBelowMin_throws() {
        registerBank("ICICI", "ICICI Bank", false);

        User u = createUser("j@x.com", "9000000010");
        Account from = createAccount(u.getId(), new BigDecimal("300000"));

        InterBankTransferRequest req = new InterBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setBeneficiaryBankCode("ICICI");
        req.setBeneficiaryAccountNumber("9876543210");
        req.setBeneficiaryName("Ravi");
        req.setBeneficiaryIfsc("ICIC0000001");
        req.setAmount(new BigDecimal("50000")); // < 200000 RTGS min
        req.setChannel(PaymentChannel.RTGS);

        assertThatThrownBy(() -> transferService.interBankTransfer(u.getId(), req))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("RTGS minimum");
    }

    @Test
    void interBankTransfer_toSelfBank_rejected() {
        registerBank("MYBK2", "My Bank", true);

        User u = createUser("k@x.com", "9000000011");
        Account from = createAccount(u.getId(), new BigDecimal("5000"));

        InterBankTransferRequest req = new InterBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setBeneficiaryBankCode("MYBK2");
        req.setBeneficiaryAccountNumber("1234567890");
        req.setBeneficiaryName("Self");
        req.setBeneficiaryIfsc("MYBK0000001");
        req.setAmount(new BigDecimal("500"));
        req.setChannel(PaymentChannel.NEFT);

        assertThatThrownBy(() -> transferService.interBankTransfer(u.getId(), req))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("use intra-bank");
    }
}
