package com.banking_software.BankingSoftware.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks the lifecycle of an outgoing/incoming transfer with another bank.
 * Created when the user initiates a transfer; updated as the rail
 * (NEFT/RTGS/IMPS/UPI) acknowledges, clears, and settles.
 */
@Entity
@Data
@Table(name = "inter_bank_transfers",
        indexes = {
                @Index(columnList = "transactionRef"),
                @Index(columnList = "status"),
                @Index(columnList = "initiatedAt")
        })
public class InterBankTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransferFlow flow = TransferFlow.OUTGOING;

    /** Our account for OUTGOING (debited); null for INCOMING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    /** Our account for INCOMING (credited); null for OUTGOING. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    /** The other bank, regardless of direction. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_bank_id", nullable = false)
    private Bank beneficiaryBank;

    @Column(nullable = false, length = 20)
    private String beneficiaryAccountNumber;

    @Column(nullable = false, length = 150)
    private String beneficiaryName;

    @Column(length = 11)
    private String beneficiaryIfsc;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.INITIATED;

    /** UTR/RRN from the payment rail. */
    @Column(length = 40)
    private String externalRef;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 255)
    private String remarks;

    @Column(nullable = false)
    private LocalDateTime initiatedAt = LocalDateTime.now();

    private LocalDateTime acknowledgedAt;
    private LocalDateTime settledAt;
}
