package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.dto.InterBankTransferRequest;
import com.banking_software.BankingSoftware.dto.RailStatementEntry;
import com.banking_software.BankingSoftware.dto.TransferResponse;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.repository.BankRepository;
import com.banking_software.BankingSoftware.service.AccountService;
import com.banking_software.BankingSoftware.service.ReconciliationService;
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
class ReconciliationTest {

    @Autowired UserService userService;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired ReconciliationService reconciliationService;
    @Autowired BankRepository bankRepo;

    private String sendTransfer(Long userId, Account acc, String bankCode, BigDecimal amount) {
        InterBankTransferRequest req = new InterBankTransferRequest();
        req.setFromAccountNumber(acc.getAccountNumber());
        req.setBeneficiaryBankCode(bankCode);
        req.setBeneficiaryAccountNumber("1234567890");
        req.setBeneficiaryName("X");
        req.setBeneficiaryIfsc("ABCD0000001");
        req.setAmount(amount);
        req.setChannel(PaymentChannel.NEFT);
        TransferResponse r = transferService.interBankTransfer(userId, req);
        return r.getExternalRef();
    }

    @Test
    void reconciliation_detectsAllFourExceptionTypes() {
        Bank b = new Bank();
        b.setBankCode("RECON");
        b.setName("Recon Bank");
        b.setSelf(false);
        bankRepo.save(b);

        CreateUserRequest ur = new CreateUserRequest();
        ur.setFirstName("R");
        ur.setEmail("recon@x.com");
        ur.setPhone("9345000001");
        User u = userService.create(ur);

        CreateAccountRequest ar = new CreateAccountRequest();
        ar.setUserId(u.getId());
        ar.setType(AccountType.SAVINGS);
        ar.setInitialDeposit(new BigDecimal("100000"));
        Account acc = accountService.create(ar);

        // 4 transfers of 1000, 2000, 3000, 4000
        String utrMatch = sendTransfer(u.getId(), acc, "RECON", new BigDecimal("1000"));
        String utrAmtBad = sendTransfer(u.getId(), acc, "RECON", new BigDecimal("2000"));
        String utrStatusBad = sendTransfer(u.getId(), acc, "RECON", new BigDecimal("3000"));
        String utrMissing = sendTransfer(u.getId(), acc, "RECON", new BigDecimal("4000"));

        List<RailStatementEntry> rail = List.of(
                entry(utrMatch, "1000", "SETTLED"),
                entry(utrAmtBad, "1999", "SETTLED"),     // amount mismatch
                entry(utrStatusBad, "3000", "FAILED"),   // status mismatch (we say ACK, rail says FAILED)
                // utrMissing absent -> MISSING_EXTERNAL
                entry("UTR-GHOST-001", "500", "SETTLED") // MISSING_INTERNAL
        );

        ReconciliationService.ReconciliationResult result =
                reconciliationService.reconcile(LocalDate.now(), rail);

        assertThat(result.matched()).isGreaterThanOrEqualTo(1);

        List<ReconciliationException> exceptions = result.exceptions();

        // Look up each expected exception by externalRef / type
        ReconciliationException amtMismatch = findByRefAndType(exceptions,
                utrAmtBad, ReconciliationExceptionType.AMOUNT_MISMATCH);
        ReconciliationException statusMismatch = findByRefAndType(exceptions,
                utrStatusBad, ReconciliationExceptionType.STATUS_MISMATCH);
        ReconciliationException missingExt = findByRefAndType(exceptions,
                utrMissing, ReconciliationExceptionType.MISSING_EXTERNAL);
        ReconciliationException missingInt = findByRefAndType(exceptions,
                "UTR-GHOST-001", ReconciliationExceptionType.MISSING_INTERNAL);

        assertThat(amtMismatch).isNotNull();
        assertThat(statusMismatch).isNotNull();
        assertThat(missingExt).isNotNull();
        assertThat(missingInt).isNotNull();

        assertThat(amtMismatch.getExpectedAmount()).isEqualByComparingTo("2000");
        assertThat(amtMismatch.getActualAmount()).isEqualByComparingTo("1999");
    }

    private ReconciliationException findByRefAndType(
            List<ReconciliationException> list, String ref, ReconciliationExceptionType type) {
        return list.stream()
                .filter(e -> ref.equals(e.getExternalRef()) && e.getType() == type)
                .findFirst().orElse(null);
    }

    private RailStatementEntry entry(String ref, String amount, String status) {
        RailStatementEntry e = new RailStatementEntry();
        e.setExternalRef(ref);
        e.setAmount(new BigDecimal(amount));
        e.setStatus(status);
        return e;
    }
}
