package com.banking_software.BankingSoftware.entity;

public enum ReconciliationExceptionType {
    /** Rail reported a txn we have no record of. */
    MISSING_INTERNAL,
    /** We have a txn the rail did not acknowledge. */
    MISSING_EXTERNAL,
    /** Both sides present, amounts differ. */
    AMOUNT_MISMATCH,
    /** Both sides present, status differs (e.g. we say ACKNOWLEDGED, rail says FAILED). */
    STATUS_MISMATCH
}
