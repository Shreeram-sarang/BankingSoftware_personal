package com.banking_software.BankingSoftware.controller;

import com.banking_software.BankingSoftware.dto.RailStatementEntry;
import com.banking_software.BankingSoftware.entity.ReconciliationException;
import com.banking_software.BankingSoftware.repository.ReconciliationExceptionRepository;
import com.banking_software.BankingSoftware.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationExceptionRepository exceptionRepo;

    @PostMapping("/run")
    public ReconciliationService.ReconciliationResult run(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody List<RailStatementEntry> entries) {
        return reconciliationService.reconcile(date, entries);
    }

    @GetMapping("/exceptions")
    public List<ReconciliationException> listOpen() {
        return exceptionRepo.findByResolvedAtIsNull();
    }
}
