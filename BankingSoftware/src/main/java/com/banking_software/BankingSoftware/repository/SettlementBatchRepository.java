package com.banking_software.BankingSoftware.repository;

import com.banking_software.BankingSoftware.entity.PaymentChannel;
import com.banking_software.BankingSoftware.entity.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {
    Optional<SettlementBatch> findBySettlementDateAndCounterpartyBankIdAndChannel(
            LocalDate date, Long bankId, PaymentChannel channel);
}
