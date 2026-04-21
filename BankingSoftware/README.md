# BankingSoftware

A Spring Boot banking back-office that models the real-world plumbing behind retail payments: customer accounts, a double-entry ledger, intra-bank transfers, inter-bank transfers over mocked NEFT/RTGS/IMPS/UPI rails, day-wise net settlement with a central clearing house, and reconciliation against rail statements.

## Stack

- Java 17, Spring Boot 3.3.4
- Spring Data JPA + Hibernate
- MySQL (prod) / H2 in MySQL-compat mode (tests)
- Flyway for schema migrations
- ShedLock for scheduler coordination across replicas
- Lombok, Bean Validation, JUnit 5

## Domain model

- **User** — KYC details, unique email/phone/PAN.
- **Account** — belongs to a user; holds `balance` and `availableBalance`; `@Version` optimistic locking.
- **Bank** — counterparty directory; `isSelf=true` marks our own bank.
- **Transaction** — ledger entry. Every debit/credit is one row; a transfer is two rows sharing `transactionRef` (double-entry).
- **InterBankTransfer** — lifecycle record for outgoing/incoming transfers: `INITIATED → VALIDATED → SENT_TO_RAIL → ACKNOWLEDGED → CLEARED → SETTLED` (with `FAILED/RETURNED/REVERSED` branches).
- **SettlementBatch** — day-wise net obligation per `(settlementDate, counterpartyBank, channel)`; `netAmount = totalOutgoing − totalIncoming`.
- **IdempotencyKey** — `(idemKey, endpoint)` unique; caches response JSON.
- **ReconciliationException** — mismatch/orphan/missing rows flagged after matching rail statements against our transfers.

## Key flows

### Intra-bank transfer
`POST /api/transfers/intra` with `X-User-Id` and `Idempotency-Key`. Both accounts are locked in deterministic order (lower id first) to avoid deadlocks; one transaction writes a DEBIT + CREDIT pair.

### Inter-bank transfer (outgoing)
`POST /api/transfers/inter`. Validates per-transaction/daily limits + RTGS min/max from `LimitsConfig`, posts the debit, and hands the message to `PaymentRailAdapter`. Rail rejection → compensating credit + `REVERSED`. Otherwise the transfer sits in `SENT_TO_RAIL`/`CLEARED` until EOD settlement picks it up.

### Inbound transfer
`POST /api/transfers/inbound`. Idempotent on the rail's UTR (`externalRef`): duplicate delivery is a no-op. Credits the destination account and creates an incoming-leg `InterBankTransfer`.

### End-of-day settlement
`SettlementService.runEndOfDay()` fires at 23:30 (cron `0 30 23 * * *`). Groups cleared transfers by `(bank, channel)`, computes `net = outgoing − incoming`, submits to `ClearingHouseAdapter`, stamps the batch `SETTLED`, and posts a NOSTRO-side entry. Guarded by `@SchedulerLock` so only one replica runs the job.

### Reconciliation
`POST /api/reconciliation/run?date=...` takes rail statement entries and joins them to our transfers on `externalRef`, emitting four exception types: `MISSING_AT_US`, `MISSING_AT_RAIL`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`.

## Safety features

- Pessimistic row locking for balance-changing reads (`findByIdForUpdate`).
- Idempotency keyed on `(idemKey, endpoint)`, user-bound (same key from a different user → rejected).
- Ownership enforced on all authenticated endpoints (`assertOwnership`).
- House/NOSTRO account allowed to go negative via a separate `postHouseDebit` path; customer accounts cannot.
- Money columns are `DECIMAL(19,2)`; never floats.

## Running

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew test          # H2, Flyway disabled, ddl-auto=create-drop
./gradlew bootRun       # needs MySQL; Flyway V1 + V2 migrate on boot
```

MySQL config in `src/main/resources/application.properties`.

## Layout

```
src/main/java/.../
  entity/        JPA entities + enums
  repository/    Spring Data repositories
  service/       business logic (transfer, settlement, reconciliation, idempotency)
  controller/    REST endpoints
  external/      mocked rail + clearing-house adapters
  config/        LimitsConfig, ShedLockConfig
src/main/resources/
  db/migration/  V1__init_schema.sql, V2__shedlock.sql
src/test/java/.../
  BankingFlowTest, SettlementTest, IdempotencyAndSecurityTest,
  ConcurrencyTest, InboundTransferTest, ReconciliationTest
```
