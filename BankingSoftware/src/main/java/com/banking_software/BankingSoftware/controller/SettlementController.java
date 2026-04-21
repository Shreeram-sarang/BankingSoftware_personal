package com.banking_software.BankingSoftware.controller;

import com.banking_software.BankingSoftware.entity.SettlementBatch;
import com.banking_software.BankingSoftware.repository.SettlementBatchRepository;
import com.banking_software.BankingSoftware.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementBatchRepository batchRepo;

    @PostMapping("/run")
    public List<SettlementBatch> run(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return settlementService.runForDate(date != null ? date : LocalDate.now());
    }

    @GetMapping
    public List<SettlementBatch> list() {
        return batchRepo.findAll();
    }
}
