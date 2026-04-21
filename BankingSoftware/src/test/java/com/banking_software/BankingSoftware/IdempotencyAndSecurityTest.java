package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.dto.*;
import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.service.AccountService;
import com.banking_software.BankingSoftware.service.IdempotencyService;
import com.banking_software.BankingSoftware.service.TransferService;
import com.banking_software.BankingSoftware.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotencyAndSecurityTest {

    @Autowired UserService userService;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired IdempotencyService idempotencyService;
    @Autowired AccountRepository accountRepo;

    private User newUser(String email, String phone) {
        CreateUserRequest r = new CreateUserRequest();
        r.setFirstName("T");
        r.setEmail(email);
        r.setPhone(phone);
        return userService.create(r);
    }

    private Account newAccount(Long userId, BigDecimal deposit) {
        CreateAccountRequest r = new CreateAccountRequest();
        r.setUserId(userId);
        r.setType(AccountType.SAVINGS);
        r.setInitialDeposit(deposit);
        return accountService.create(r);
    }

    @Test
    void ownership_userCannotTransferFromAnotherUsersAccount() {
        User owner = newUser("own@x.com", "9500000001");
        User attacker = newUser("atk@x.com", "9500000002");
        User receiver = newUser("rcv@x.com", "9500000003");

        Account from = newAccount(owner.getId(), new BigDecimal("10000"));
        Account to = newAccount(receiver.getId(), BigDecimal.ZERO);

        IntraBankTransferRequest req = new IntraBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setToAccountNumber(to.getAccountNumber());
        req.setAmount(new BigDecimal("100"));

        assertThatThrownBy(() -> transferService.intraBankTransfer(attacker.getId(), req))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("does not belong to caller");

        // balance untouched
        Account after = accountRepo.findById(from.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("10000");
    }

    @Test
    void idempotency_replaySameKeyReturnsCachedResponseWithoutDoubleDebit() {
        User u1 = newUser("i1@x.com", "9600000001");
        User u2 = newUser("i2@x.com", "9600000002");
        Account from = newAccount(u1.getId(), new BigDecimal("10000"));
        Account to = newAccount(u2.getId(), BigDecimal.ZERO);

        IntraBankTransferRequest req = new IntraBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setToAccountNumber(to.getAccountNumber());
        req.setAmount(new BigDecimal("250"));

        String key = UUID.randomUUID().toString();

        TransferResponse first = idempotencyService.execute(
                key, "transfers.intra", u1.getId(), TransferResponse.class,
                () -> transferService.intraBankTransfer(u1.getId(), req));

        TransferResponse second = idempotencyService.execute(
                key, "transfers.intra", u1.getId(), TransferResponse.class,
                () -> transferService.intraBankTransfer(u1.getId(), req));

        assertThat(second.getTransactionRef()).isEqualTo(first.getTransactionRef());

        // Only ONE debit happened — balance is 10000 - 250, not 10000 - 500.
        Account after = accountRepo.findById(from.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("9750");
    }

    @Test
    void idempotency_sameKeyDifferentUser_rejected() {
        User u1 = newUser("ia@x.com", "9700000001");
        User u2 = newUser("ib@x.com", "9700000002");
        Account from = newAccount(u1.getId(), new BigDecimal("10000"));
        Account to = newAccount(u2.getId(), BigDecimal.ZERO);

        IntraBankTransferRequest req = new IntraBankTransferRequest();
        req.setFromAccountNumber(from.getAccountNumber());
        req.setToAccountNumber(to.getAccountNumber());
        req.setAmount(new BigDecimal("50"));

        String key = UUID.randomUUID().toString();
        idempotencyService.execute(key, "transfers.intra", u1.getId(),
                TransferResponse.class,
                () -> transferService.intraBankTransfer(u1.getId(), req));

        assertThatThrownBy(() -> idempotencyService.execute(
                key, "transfers.intra", u2.getId(), TransferResponse.class,
                () -> transferService.intraBankTransfer(u2.getId(), req)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("used by a different user");
    }

    @Test
    void idempotency_missingKey_rejected() {
        assertThatThrownBy(() -> idempotencyService.execute(
                null, "transfers.intra", 1L, TransferResponse.class, () -> null))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Idempotency-Key header is required");
    }
}
