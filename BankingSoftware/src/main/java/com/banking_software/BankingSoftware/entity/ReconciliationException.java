package com.banking_software.BankingSoftware.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "reconciliation_exceptions",
        indexes = {
                @Index(columnList = "statementDate"),
                @Index(columnList = "externalRef"),
                @Index(columnList = "resolvedAt")
        })
public class ReconciliationException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate statementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationExceptionType type;

    @Column(length = 40)
    private String externalRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inter_bank_transfer_id")
    private InterBankTransfer interBankTransfer;

    @Column(precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(length = 20)
    private String expectedStatus;

    @Column(length = 20)
    private String actualStatus;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;
    private String resolution;
}
