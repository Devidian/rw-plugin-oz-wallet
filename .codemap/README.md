# OZ Wallet
<!-- codemap-id: rw-plugin-oz-wallet -->

## Purpose

Owns currencies, player balances, transaction history, system accounts and world treasury. [Descriptor](../src/resources/plugin.yml) starts [`Wallet`](../src/de/omegazirkel/risingworld/Wallet.java), which registers the Rising World listener and delegates lifecycle and events to [`WalletRuntime`](../src/de/omegazirkel/risingworld/WalletRuntime.java).

## Modules

- [Plugin API and ledger](modules/ledger.md) for sibling integrations, idempotent transfers, audit history and SQLite.
- [Runtime and player UI](modules/runtime.md) for settings, commands, overlays and HUD.

## Build and tests

[Maven](../pom.xml) builds the plugin against Rising World and OZ Tools. Run `mvn -B test` for [wallet tests](../tests/de/omegazirkel/risingworld/wallet/) and use the [repository workflow](../AGENTS.md) for packaging and PluginAPI checks.

## Dependencies

- `rw-plugin-oz-tools/shared-api` — build and in-server runtime dependency for settings, UI, SQLite and shared services. [Build configuration](../pom.xml)
