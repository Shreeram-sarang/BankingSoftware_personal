package com.banking_software.BankingSoftware.repository;

import com.banking_software.BankingSoftware.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {
    Optional<Bank> findByBankCode(String bankCode);
    Optional<Bank> findByIsSelfTrue();
}
