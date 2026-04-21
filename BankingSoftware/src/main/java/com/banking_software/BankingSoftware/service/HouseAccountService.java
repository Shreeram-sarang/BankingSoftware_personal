package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.entity.*;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Owns the bank's internal "house" accounts: the NOSTRO account used to post
 * net settlement entries. Lazily created on first use.
 */
@Service
@RequiredArgsConstructor
public class HouseAccountService {

    private static final String HOUSE_EMAIL = "house@bank.internal";
    private static final String HOUSE_PHONE = "0000000000";
    private static final String NOSTRO_ACCOUNT_NUMBER = "0000000000001";

    private final UserRepository userRepo;
    private final AccountRepository accountRepo;

    @Transactional
    public Account getOrCreateNostro() {
        return accountRepo.findByAccountNumber(NOSTRO_ACCOUNT_NUMBER)
                .orElseGet(this::createNostro);
    }

    private Account createNostro() {
        User house = userRepo.findByEmail(HOUSE_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setFirstName("HOUSE");
            u.setLastName("SYSTEM");
            u.setEmail(HOUSE_EMAIL);
            u.setPhone(HOUSE_PHONE);
            u.setKycStatus(KycStatus.VERIFIED);
            return userRepo.save(u);
        });

        Account a = new Account();
        a.setAccountNumber(NOSTRO_ACCOUNT_NUMBER);
        a.setUser(house);
        a.setType(AccountType.NOSTRO);
        a.setStatus(AccountStatus.ACTIVE);
        a.setBalance(BigDecimal.ZERO);
        a.setAvailableBalance(BigDecimal.ZERO);
        a.setIfsc("MYBK0000001");
        a.setBranch("House");
        return accountRepo.save(a);
    }
}
