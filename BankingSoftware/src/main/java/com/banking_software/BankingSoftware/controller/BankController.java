package com.banking_software.BankingSoftware.controller;

import com.banking_software.BankingSoftware.entity.Bank;
import com.banking_software.BankingSoftware.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankRepository bankRepo;

    @PostMapping
    public Bank create(@RequestBody Bank bank) {
        return bankRepo.save(bank);
    }

    @GetMapping
    public List<Bank> list() {
        return bankRepo.findAll();
    }
}
