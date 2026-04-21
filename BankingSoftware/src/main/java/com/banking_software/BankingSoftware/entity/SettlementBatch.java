package com.banking_software.BankingSoftware.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Day-wise net settlement batch against a counterparty bank.
 * End-of-day, all cleared inter-bank transfers with that bank are
 * netted into a single obligation (we pay them, or they pay us).
 */
@Entity
@Data
@Table(name = "settlement_batches",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"settlement_date", "counterparty_bank_id", "channel"}),
        indexes = {
                @Index(columnList = "settlementDate"),
                @Index(columnList = "status")
        })
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate settlementDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_bank_id", nullable = false)
    private Bank counterpartyBank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentChannel channel;

    /** Sum of amounts we owe them (our outgoing). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalOutgoing = BigDecimal.ZERO;

    /** Sum of amounts they owe us (their outgoing to our customers). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalIncoming = BigDecimal.ZERO;

    /** netAmount = totalOutgoing - totalIncoming. Positive: we pay. Negative: we receive. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private int transactionCount = 0;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.OPEN;

    /** Clearing-house reference (e.g. RBI / NPCI file id). */
    @Column(length = 60)
    private String clearingHouseRef;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime closedAt;
    private LocalDateTime settledAt;
}
