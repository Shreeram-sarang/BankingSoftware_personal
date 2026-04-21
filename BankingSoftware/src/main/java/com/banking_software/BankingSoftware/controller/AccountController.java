package com.banking_software.BankingSoftware.controller;

import com.banking_software.BankingSoftware.dto.CreateAccountRequest;
import com.banking_software.BankingSoftware.entity.Account;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.AccountRepository;
import com.banking_software.BankingSoftware.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountRepository accountRepo;

    @PostMapping
    public Account create(@Valid @RequestBody CreateAccountRequest req) {
        return accountService.create(req);
    }

    @GetMapping("/{accountNumber}")
    public Account get(@PathVariable String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankingException("ACCOUNT_NOT_FOUND", "Not found"));
    }

    @GetMapping("/user/{userId}")
    public List<Account> listByUser(@PathVariable Long userId) {
        return accountRepo.findByUserId(userId);
    }
}
