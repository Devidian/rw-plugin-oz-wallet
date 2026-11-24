# Wallet API and ledger
<!-- codemap-id: rw-plugin-oz-wallet/ledger -->

## Responsibility

Expose Wallet's player balance, currency and system-account APIs through the loaded plugin. Own the SQLite ledger, idempotency and account transfer rules. Sibling plugins call Wallet's facade and consume its result types.

## Source and tests

- [`WalletRuntime`](../../src/de/omegazirkel/risingworld/WalletRuntime.java) exposes public operations on the loaded `Wallet`, including `deposit`, `withdraw`, `balance`, `transferIdempotent`, and player/system/world transfer methods.
- [`WalletService`](../../src/de/omegazirkel/risingworld/wallet/WalletService.java) validates requests and delegates ledger mutations. [`WalletDatabase`](../../src/de/omegazirkel/risingworld/wallet/WalletDatabase.java) owns schemas, transaction boundaries, correlation lookup, and rollback.
- [`WorldTreasuryService`](../../src/de/omegazirkel/risingworld/wallet/WorldTreasuryService.java) resolves the world account. [`AccountTransferResult`](../../src/de/omegazirkel/risingworld/wallet/AccountTransferResult.java) is part of the caller-facing contract.
- [`WalletTransferTest`](../../tests/de/omegazirkel/risingworld/wallet/WalletTransferTest.java), [`SystemAccountTest`](../../tests/de/omegazirkel/risingworld/wallet/SystemAccountTest.java), and [`WalletErasureTest`](../../tests/de/omegazirkel/risingworld/wallet/WalletErasureTest.java) cover transfer, account and privacy behavior.
- See [runtime](runtime.md) for startup, settings and UI. Use the ecosystem dependency query for inbound integrations.
