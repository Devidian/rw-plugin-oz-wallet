# Wallet Idempotent Transfer Contract

## Objective

Provide an atomic, replay-safe Wallet transfer for cross-plugin custody sagas.

## Contract

- `Wallet.transferIdempotent(...)` debits the payer and credits the payee in a
  single Wallet SQLite transaction.
- A non-empty correlation ID is an immutable idempotency key.
- Exact retries return the original transfer without booking again.
- A changed request using the same correlation ID returns
  `IDEMPOTENCY_CONFLICT`.
- Neither account is changed on insufficient funds, overflow, or database
  failure.

## Rollback and validation

The Wallet SQLite transaction rolls back both balance updates, both transaction
ledger rows, and the transfer record as one unit. The regression tests cover
exact retries, conflicts, and insufficient funds.
