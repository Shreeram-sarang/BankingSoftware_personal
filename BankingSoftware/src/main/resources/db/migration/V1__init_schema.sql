-- Baseline schema for BankingSoftware. Mirrors the JPA entities.
-- All monetary amounts use DECIMAL(19,2); never use FLOAT/DOUBLE for money.

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100),
    email           VARCHAR(150) NOT NULL,
    phone           VARCHAR(15)  NOT NULL,
    pan             VARCHAR(10),
    date_of_birth   DATE,
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    state           VARCHAR(100),
    pincode         VARCHAR(10),
    kyc_status      VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_pan (pan)
) ENGINE=InnoDB;

CREATE TABLE accounts (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_number     VARCHAR(20)   NOT NULL,
    user_id            BIGINT        NOT NULL,
    type               VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    balance            DECIMAL(19,2) NOT NULL,
    available_balance  DECIMAL(19,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    ifsc               VARCHAR(11),
    branch             VARCHAR(100),
    version            BIGINT,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6),
    UNIQUE KEY uk_accounts_number (account_number),
    KEY idx_accounts_user (user_id),
    KEY idx_accounts_number (account_number),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE banks (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bank_code           VARCHAR(20)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    swift_code          VARCHAR(15),
    is_self             BIT(1)       NOT NULL,
    rtgs_identifier     VARCHAR(50),
    neft_identifier     VARCHAR(50),
    upi_handle          VARCHAR(50),
    settlement_partner  VARCHAR(100),
    active              BIT(1)       NOT NULL,
    UNIQUE KEY uk_banks_code (bank_code),
    UNIQUE KEY uk_banks_swift (swift_code)
) ENGINE=InnoDB;

CREATE TABLE inter_bank_transfers (
    id                            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_ref               VARCHAR(40)   NOT NULL,
    flow                          VARCHAR(10)   NOT NULL,
    source_account_id             BIGINT,
    destination_account_id        BIGINT,
    beneficiary_bank_id           BIGINT        NOT NULL,
    beneficiary_account_number    VARCHAR(20)   NOT NULL,
    beneficiary_name              VARCHAR(150)  NOT NULL,
    beneficiary_ifsc              VARCHAR(11),
    amount                        DECIMAL(19,2) NOT NULL,
    fee_amount                    DECIMAL(19,2),
    currency                      VARCHAR(3)    NOT NULL,
    channel                       VARCHAR(10)   NOT NULL,
    status                        VARCHAR(20)   NOT NULL,
    external_ref                  VARCHAR(40),
    failure_reason                VARCHAR(500),
    remarks                       VARCHAR(255),
    initiated_at                  DATETIME(6)   NOT NULL,
    acknowledged_at               DATETIME(6),
    settled_at                    DATETIME(6),
    UNIQUE KEY uk_ibt_ref (transaction_ref),
    KEY idx_ibt_ref (transaction_ref),
    KEY idx_ibt_status (status),
    KEY idx_ibt_initiated (initiated_at),
    KEY idx_ibt_external (external_ref),
    CONSTRAINT fk_ibt_source  FOREIGN KEY (source_account_id)      REFERENCES accounts (id),
    CONSTRAINT fk_ibt_dest    FOREIGN KEY (destination_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_ibt_bank    FOREIGN KEY (beneficiary_bank_id)    REFERENCES banks (id)
) ENGINE=InnoDB;

CREATE TABLE settlement_batches (
    id                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    settlement_date         DATE          NOT NULL,
    counterparty_bank_id    BIGINT        NOT NULL,
    channel                 VARCHAR(10)   NOT NULL,
    total_outgoing          DECIMAL(19,2) NOT NULL,
    total_incoming          DECIMAL(19,2) NOT NULL,
    net_amount              DECIMAL(19,2) NOT NULL,
    transaction_count       INT           NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    clearing_house_ref      VARCHAR(60),
    created_at              DATETIME(6)   NOT NULL,
    closed_at               DATETIME(6),
    settled_at              DATETIME(6),
    UNIQUE KEY uk_batch_day_bank_channel (settlement_date, counterparty_bank_id, channel),
    KEY idx_batch_date (settlement_date),
    KEY idx_batch_status (status),
    CONSTRAINT fk_batch_bank FOREIGN KEY (counterparty_bank_id) REFERENCES banks (id)
) ENGINE=InnoDB;

CREATE TABLE transactions (
    id                             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_ref                VARCHAR(40)   NOT NULL,
    account_id                     BIGINT        NOT NULL,
    direction                      VARCHAR(10)   NOT NULL,
    type                           VARCHAR(20)   NOT NULL,
    status                         VARCHAR(20)   NOT NULL,
    amount                         DECIMAL(19,2) NOT NULL,
    balance_after                  DECIMAL(19,2),
    currency                       VARCHAR(3)    NOT NULL,
    counterparty_bank_id           BIGINT,
    counterparty_account_number    VARCHAR(20),
    counterparty_name              VARCHAR(150),
    counterparty_ifsc              VARCHAR(11),
    external_ref                   VARCHAR(40),
    channel                        VARCHAR(20),
    description                    VARCHAR(255),
    value_date                     DATE          NOT NULL,
    posted_at                      DATETIME(6)   NOT NULL,
    settlement_batch_id            BIGINT,
    KEY idx_txn_account (account_id),
    KEY idx_txn_ref (transaction_ref),
    KEY idx_txn_value_date (value_date),
    KEY idx_txn_status (status),
    CONSTRAINT fk_txn_account  FOREIGN KEY (account_id)            REFERENCES accounts (id),
    CONSTRAINT fk_txn_cp_bank  FOREIGN KEY (counterparty_bank_id)  REFERENCES banks (id),
    CONSTRAINT fk_txn_batch    FOREIGN KEY (settlement_batch_id)   REFERENCES settlement_batches (id)
) ENGINE=InnoDB;

CREATE TABLE idempotency_keys (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    idem_key       VARCHAR(80)  NOT NULL,
    endpoint       VARCHAR(80)  NOT NULL,
    user_id        BIGINT       NOT NULL,
    response_json  LONGTEXT     NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_idem_key_endpoint (idem_key, endpoint)
) ENGINE=InnoDB;

CREATE TABLE reconciliation_exceptions (
    id                        BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    statement_date            DATE          NOT NULL,
    type                      VARCHAR(20)   NOT NULL,
    external_ref              VARCHAR(40),
    inter_bank_transfer_id    BIGINT,
    expected_amount           DECIMAL(19,2),
    actual_amount             DECIMAL(19,2),
    expected_status           VARCHAR(20),
    actual_status             VARCHAR(20),
    reason                    VARCHAR(500),
    created_at                DATETIME(6)   NOT NULL,
    resolved_at               DATETIME(6),
    resolution                VARCHAR(255),
    KEY idx_recon_date (statement_date),
    KEY idx_recon_ref (external_ref),
    KEY idx_recon_resolved (resolved_at),
    CONSTRAINT fk_recon_ibt FOREIGN KEY (inter_bank_transfer_id) REFERENCES inter_bank_transfers (id)
) ENGINE=InnoDB;
