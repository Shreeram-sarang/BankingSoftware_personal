package com.banking_software.BankingSoftware.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ledger entry. Every debit/credit creates one row.
 * A transfer = two rows sharing the same transactionRef (double-entry).
 */
@Entity
@Data
@Table(name = "transactions",
        indexes = {
                @Index(columnList = "account_id"),
                @Index(columnList = "transactionRef"),
                @Index(columnList = "valueDate"),
                @Index(columnList = "status")
        })
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shared reference across legs of the same logical transaction. */
    @Column(nullable = false, length = 40)
    private String transactionRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    /** For inter-bank transfers. Null for intra-bank. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_bank_id")
    private Bank counterpartyBank;

    @Column(length = 20)
    private String counterpartyAccountNumber;

    @Column(length = 150)
    private String counterpartyName;

    @Column(length = 11)
    private String counterpartyIfsc;

    /** External reference from NEFT/RTGS/UPI/IMPS rail. */
    @Column(length = 40)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentChannel channel;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private LocalDate valueDate;

    @Column(nullable = false)
    private LocalDateTime postedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_batch_id")
    private SettlementBatch settlementBatch;
}
