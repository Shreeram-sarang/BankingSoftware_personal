package com.banking_software.BankingSoftware.entity;

public enum TransferStatus {
    INITIATED,
    VALIDATED,
    SENT_TO_RAIL,
    ACKNOWLEDGED,
    CLEARED,
    SETTLED,
    FAILED,
    RETURNED,
    REVERSED
}
