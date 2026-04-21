package com.banking_software.BankingSoftware.repository;

import com.banking_software.BankingSoftware.entity.ReconciliationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReconciliationExceptionRepository extends JpaRepository<ReconciliationException, Long> {
    List<ReconciliationException> findByStatementDate(LocalDate date);
    List<ReconciliationException> findByResolvedAtIsNull();
}
