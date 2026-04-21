package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.dto.InboundTransferRequest;
import com.banking_software.BankingSoftware.dto.InterBankTransferRequest;
import com.banking_software.BankingSoftware.dto.TransferResponse;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.repository.BankRepository;
import com.banking_software.BankingSoftware.service.AccountService;
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
class InboundTransferTest {

    @Autowired UserService userService;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired SettlementService settlementService;
    @Autowired BankRepository bankRepo;
    @Autowired AccountRepository accountRepo;

    @Test
    void inbound_creditsAccountAndIsIdempotentOnExternalRef() {
        Bank origin = new Bank();
        origin.setBankCode("SBI_IN");
        origin.setName("SBI");
        origin.setSelf(false);
        bankRepo.save(origin);

        CreateUserRequest ur = new CreateUserRequest();
        ur.setFirstName("In");
        ur.setEmail("in@x.com");
        ur.setPhone("9123456001");
        User u = userService.create(ur);

        CreateAccountRequest ar = new CreateAccountRequest();
        ar.setUserId(u.getId());
        ar.setType(AccountType.SAVINGS);
        ar.setInitialDeposit(new BigDecimal("1000"));
        Account acc = accountService.create(ar);

        InboundTransferRequest req = new InboundTransferRequest();
        req.setOriginatorBankCode("SBI_IN");
        req.setOriginatorAccountNumber("55566677788");
        req.setOriginatorName("Alice");
        req.setDestinationAccountNumber(acc.getAccountNumber());
        req.setAmount(new BigDecimal("7500"));
        req.setChannel(PaymentChannel.NEFT);
        req.setExternalRef("UTR-123-ABC");

        TransferResponse r1 = transferService.inboundTransfer(req);
        TransferResponse r2 = transferService.inboundTransfer(req); // replay same UTR

        assertThat(r1.getTransactionRef()).isEqualTo(r2.getTransactionRef());

        Account after = accountRepo.findById(acc.getId()).orElseThrow();
        // Only one credit of 7500 should have landed.
        assertThat(after.getBalance()).isEqualByComparingTo("8500");
    }

    @Test
    void settlement_netsOutgoingAgainstIncomingForSameBank() {
        Bank hdfc = new Bank();
        hdfc.setBankCode("HDFC_NET");
        hdfc.setName("HDFC");
        hdfc.setSelf(false);
        bankRepo.save(hdfc);

        CreateUserRequest ur = new CreateUserRequest();
        ur.setFirstName("N");
        ur.setEmail("net@x.com");
        ur.setPhone("9123456002");
        User u = userService.create(ur);

        CreateAccountRequest ar = new CreateAccountRequest();
        ar.setUserId(u.getId());
        ar.setType(AccountType.SAVINGS);
        ar.setInitialDeposit(new BigDecimal("50000"));
        Account acc = accountService.create(ar);

        // Outgoing 10000 to HDFC
        InterBankTransferRequest out = new InterBankTransferRequest();
        out.setFromAccountNumber(acc.getAccountNumber());
        out.setBeneficiaryBankCode("HDFC_NET");
        out.setBeneficiaryAccountNumber("9998887771");
        out.setBeneficiaryName("Bob");
        out.setBeneficiaryIfsc("HDFC0000099");
        out.setAmount(new BigDecimal("10000"));
        out.setChannel(PaymentChannel.NEFT);
        transferService.interBankTransfer(u.getId(), out);

        // Incoming 3000 from HDFC
        InboundTransferRequest in = new InboundTransferRequest();
        in.setOriginatorBankCode("HDFC_NET");
        in.setOriginatorAccountNumber("9998887771");
        in.setOriginatorName("Bob");
        in.setDestinationAccountNumber(acc.getAccountNumber());
        in.setAmount(new BigDecimal("3000"));
        in.setChannel(PaymentChannel.NEFT);
        in.setExternalRef("UTR-NET-001");
        transferService.inboundTransfer(in);

        List<SettlementBatch> batches = settlementService.runForDate(LocalDate.now());

        SettlementBatch hdfcBatch = batches.stream()
                .filter(b -> b.getCounterpartyBank().getBankCode().equals("HDFC_NET"))
                .findFirst().orElseThrow();

        assertThat(hdfcBatch.getTotalOutgoing()).isEqualByComparingTo("10000");
        assertThat(hdfcBatch.getTotalIncoming()).isEqualByComparingTo("3000");
        assertThat(hdfcBatch.getNetAmount()).isEqualByComparingTo("7000"); // we owe 7k net
        assertThat(hdfcBatch.getTransactionCount()).isEqualTo(2);
    }
}
