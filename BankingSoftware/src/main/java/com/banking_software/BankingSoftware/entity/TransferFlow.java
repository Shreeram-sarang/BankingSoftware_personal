package com.banking_software.BankingSoftware.entity;

public enum TransferFlow {
    /** We originated; money leaves our bank. */
    OUTGOING,
    /** Another bank originated; money arrives at one of our accounts. */
    INCOMING
}
