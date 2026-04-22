package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.dto.InterBankTransferRequest;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.repository.BankRepository;
import com.banking_software.BankingSoftware.repository.InterBankTransferRepository;
import com.banking_software.BankingSoftware.service.AccountService;
import com.banking_software.BankingSoftware.service.HouseAccountService;
import com.banking_software.BankingSoftware.service.SettlementService;
import com.banking_software.BankingSoftware.service.TransferService;
import com.banking_software.BankingSoftware.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SettlementTest {

    @Autowired UserService userService;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired SettlementService settlementService;
    @Autowired HouseAccountService houseAccountService;
    @Autowired BankRepository bankRepo;
    @Autowired InterBankTransferRepository transferRepo;

    @Test
    void eodSettlement_netsTransfersAndMarksSettled() {
        Bank hdfc = new Bank();
        hdfc.setBankCode("HDFC_S");
        hdfc.setName("HDFC Bank");
        hdfc.setSelf(false);
        bankRepo.save(hdfc);

        Bank icici = new Bank();
        icici.setBankCode("ICICI_S");
        icici.setName("ICICI Bank");
        icici.setSelf(false);
        bankRepo.save(icici);

        CreateUserRequest u = new CreateUserRequest();
        u.setFirstName("Set");
        u.setEmail("settle@x.com");
        u.setPhone("9111111111");
        User user = userService.create(u);

        CreateAccountRequest ar = new CreateAccountRequest();
        ar.setUserId(user.getId());
        ar.setType(AccountType.SAVINGS);
        ar.setInitialDeposit(new BigDecimal("100000"));
        Account acc = accountService.create(ar);

        // Two NEFT transfers to HDFC, one to ICICI.
        sendNeft(user.getId(), acc, "HDFC_S", new BigDecimal("1000"));
        sendNeft(user.getId(), acc, "HDFC_S", new BigDecimal("2500"));
        sendNeft(user.getId(), acc, "ICICI_S", new BigDecimal("5000"));

        List<SettlementBatch> batches = settlementService.runForDate(LocalDate.now());

        // >= 2 (other tests in the suite may have contributed transfers too)
        assertThat(batches).hasSizeGreaterThanOrEqualTo(2);

        SettlementBatch hdfcBatch = batches.stream()
                .filter(b -> b.getCounterpartyBank().getBankCode().equals("HDFC_S"))
                .findFirst().orElseThrow();
        assertThat(hdfcBatch.getTransactionCount()).isEqualTo(2);
        assertThat(hdfcBatch.getNetAmount()).isEqualByComparingTo("3500");
        assertThat(hdfcBatch.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(hdfcBatch.getClearingHouseRef()).startsWith("CH-NEFT-");

        SettlementBatch iciciBatch = batches.stream()
                .filter(b -> b.getCounterpartyBank().getBankCode().equals("ICICI_S"))
                .findFirst().orElseThrow();
        assertThat(iciciBatch.getNetAmount()).isEqualByComparingTo("5000");

        // Nothing ACKNOWLEDGED or CLEARED should remain — either SETTLED or FAILED
        // (FAILED rows are left behind by tests that exercise rail rejection).
        List<InterBankTransfer> all = transferRepo.findAll();
        assertThat(all).allMatch(t ->
                t.getStatus() == TransferStatus.SETTLED
                        || t.getStatus() == TransferStatus.FAILED);

        // NOSTRO has been debited for every settled batch; must be <= -8500 (our three).
        Account nostro = houseAccountService.getOrCreateNostro();
        assertThat(nostro.getBalance()).isLessThanOrEqualTo(new BigDecimal("-8500"));
    }

    @Test
    void eodSettlement_isIdempotentForSameDay() {
        Bank axis = new Bank();
        axis.setBankCode("AXIS_S");
        axis.setName("Axis Bank");
        axis.setSelf(false);
        bankRepo.save(axis);

        CreateUserRequest u = new CreateUserRequest();
        u.setFirstName("Set2");
        u.setEmail("settle2@x.com");
        u.setPhone("9222222222");
        User user = userService.create(u);

        CreateAccountRequest ar = new CreateAccountRequest();
        ar.setUserId(user.getId());
        ar.setType(AccountType.SAVINGS);
        ar.setInitialDeposit(new BigDecimal("20000"));
        Account acc = accountService.create(ar);

        sendNeft(user.getId(), acc, "AXIS_S", new BigDecimal("500"));

        List<SettlementBatch> first = settlementService.runForDate(LocalDate.now());
        List<SettlementBatch> second = settlementService.runForDate(LocalDate.now());

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(0); // already settled, no eligible transfers left
    }

    private void sendNeft(Long userId, Account from, String bankCode, BigDecimal amount) {
        InterBankTransferRequest req = new InterBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setBeneficiaryBankCode(bankCode);
        req.setBeneficiaryAccountNumber("1234567890");
        req.setBeneficiaryName("Beneficiary");
        req.setBeneficiaryIfsc("ABCD0000001");
        req.setAmount(amount);
        req.setChannel(PaymentChannel.NEFT);
        transferService.interBankTransfer(userId, req);
    }
}
