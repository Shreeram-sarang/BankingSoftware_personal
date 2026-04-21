package com.banking_software.BankingSoftware.repository;

import com.banking_software.BankingSoftware.entity.InterBankTransfer;
import com.banking_software.BankingSoftware.entity.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterBankTransferRepository extends JpaRepository<InterBankTransfer, Long> {
    Optional<InterBankTransfer> findByTransactionRef(String ref);

    Optional<InterBankTransfer> findByExternalRef(String externalRef);

    @Query("""
            select t from InterBankTransfer t
            where t.status in :statuses
              and t.initiatedAt >= :from
              and t.initiatedAt <  :to
            """)
    List<InterBankTransfer> findEligibleForSettlement(
            @Param("statuses") List<TransferStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select t from InterBankTransfer t
            where t.initiatedAt >= :from
              and t.initiatedAt <  :to
              and t.externalRef is not null
            """)
    List<InterBankTransfer> findWithExternalRefInWindow(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}

