# Wallet System Accounts

## Objective

Add Wallet-owned non-player accounts for world revenue, city treasuries, and
future plugin-owned entities without overloading player database IDs.

## Ownership

Owning repository/plugin: `rw-plugin-oz-wallet`

Supporting consumers: `rw-plugin-oz-shop`, `rw-plugin-oz-marketplace`, and
`rw-plugin-oz-land-claim`. Consumers own their spending rules; Wallet owns all
balances, transaction history, and transfer contracts.

## Dependencies

- Runtime/build baselines remain unchanged: Java 20, PluginAPI, and OZ Tools.
- No consumer plugin is required for Wallet startup.
- Consumers call the additive public API through their optional reflection
  bridges.

## Contract and migration

- Additive SQLite schema version 3 stores system accounts, per-currency
  balances, transactions, and idempotent transfers separately from player IDs.
- Account IDs and owner plugins are immutable. An exact account-creation retry
  succeeds; ownership, type, or metadata conflicts fail. The owner can update
  display metadata through the dedicated API without changing identity.
- Only the owner plugin may debit or archive an account. Any plugin may credit
  an active account through a player-to-system transfer.
- Archive is non-destructive and requires all balances to be zero.
- The administrator overview sorts accounts by total balance descending; zero-balance archived accounts therefore
  naturally appear at the bottom.
- All transfers involving system accounts are atomic and require immutable
  correlation IDs.
- Wallet creates `world::<World_Name>` at startup with a zero balance and no
  historical backfill.

## Risks

- Consumer retries could double-charge without correlation IDs; all new transfer
  methods enforce them.
- System-account ownership could be bypassed by direct database access;
  internals remain non-public and debit APIs validate the owner plugin.
- Schema rollback must preserve audit data; new tables are additive and may
  remain unused by an older plugin.

## Validation strategy

- Unit tests cover migration, idempotent creation, ownership conflicts, every
  transfer direction, retry conflicts, insufficient funds, archive rules, and
  list filtering/pagination.
- Run Maven test/package, entrypoint checks, API verification, ZIP integrity,
  and `git diff --check`.

## Affected repositories/plugins

- `rw-plugin-oz-wallet`
- Consumers are implemented and validated in their own repositories.

## Rollback considerations

Roll consumers back before Wallet. Existing player API and tables stay
compatible. Rolling Wallet back leaves schema-version-3 tables intact; no
balances or history are deleted.

## Implementation checklist

- [x] Add schema and domain/result objects.
- [x] Add account creation, display update, lookup, pagination, balances, audit, and archive.
- [x] Add player/system and system/system idempotent transfers.
- [x] Create the current world account during startup.
- [x] Add admin list/search/pagination and transaction detail.
- [x] Sort the administrator overview by total balance descending.
- [x] Add DE/EN UI labels.
- [x] Add unit tests.
- [x] Complete package/API/entrypoint validation after consumer integration.
