package com.banking_software.BankingSoftware.repository;

import com.banking_software.BankingSoftware.entity.Transaction;
import com.banking_software.BankingSoftware.entity.TransactionDirection;
import com.banking_software.BankingSoftware.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByTransactionRef(String ref);

    List<Transaction> findByAccountIdOrderByPostedAtDesc(Long accountId);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.id = :accountId
              and t.direction = :dir
              and t.valueDate = :date
              and t.status <> com.banking_software.BankingSoftware.entity.TransactionStatus.FAILED
            """)
    BigDecimal sumByAccountAndDirectionAndDate(
            @Param("accountId") Long accountId,
            @Param("dir") TransactionDirection direction,
            @Param("date") LocalDate date);
}
