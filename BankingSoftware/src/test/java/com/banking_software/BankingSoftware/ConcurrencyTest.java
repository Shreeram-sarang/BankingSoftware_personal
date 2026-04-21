package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.dto.IntraBankTransferRequest;
import com.banking_software.BankingSoftware.entity.Account;
import com.banking_software.BankingSoftware.entity.AccountType;
import com.banking_software.BankingSoftware.entity.User;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.service.AccountService;
import com.banking_software.BankingSoftware.service.TransferService;
import com.banking_software.BankingSoftware.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTest {

    @Autowired UserService userService;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepo;

    @Test
    void parallelDebits_pessimisticLockPreventsOverdraft() throws Exception {
        CreateUserRequest u1r = new CreateUserRequest();
        u1r.setFirstName("C");
        u1r.setEmail("c1@x.com");
        u1r.setPhone("9800000001");
        User sender = userService.create(u1r);

        CreateUserRequest u2r = new CreateUserRequest();
        u2r.setFirstName("R");
        u2r.setEmail("c2@x.com");
        u2r.setPhone("9800000002");
        User receiver = userService.create(u2r);

        // 100 balance, 20 parallel attempts to debit 10 each -> exactly 10 should succeed.
        CreateAccountRequest ar = new CreateAccountRequest();
        ar.setUserId(sender.getId());
        ar.setType(AccountType.SAVINGS);
        ar.setInitialDeposit(new BigDecimal("100"));
        Account from = accountService.create(ar);

        CreateAccountRequest ar2 = new CreateAccountRequest();
        ar2.setUserId(receiver.getId());
        ar2.setType(AccountType.SAVINGS);
        ar2.setInitialDeposit(BigDecimal.ZERO);
        Account to = accountService.create(ar2);

        int threads = 20;
        BigDecimal amount = new BigDecimal("10");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    IntraBankTransferRequest req = new IntraBankTransferRequest();
                    req.setFromAccountNumber(from.getAccountNumber());
                    req.setToAccountNumber(to.getAccountNumber());
                    req.setAmount(amount);
                    transferService.intraBankTransfer(sender.getId(), req);
                    successes.incrementAndGet();
                } catch (BankingException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        Account fromAfter = accountRepo.findById(from.getId()).orElseThrow();
        Account toAfter = accountRepo.findById(to.getId()).orElseThrow();

        assertThat(successes.get()).isEqualTo(10);
        assertThat(failures.get()).isEqualTo(10);
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("0");
        assertThat(toAfter.getBalance()).isEqualByComparingTo("100");
    }
}
