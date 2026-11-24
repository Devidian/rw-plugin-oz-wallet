# Wallet runtime and UI
<!-- codemap-id: rw-plugin-oz-wallet/runtime -->

## Responsibility

Wire plugin lifecycle, wallet commands, player settings and overlays. [Descriptor](../../src/resources/plugin.yml) names `Wallet` as the sole registered listener.

## Source and tests

- [`Wallet`](../../src/de/omegazirkel/risingworld/Wallet.java) forwards enable, disable, settings refresh, commands and spawn/disconnect events to [`WalletRuntime`](../../src/de/omegazirkel/risingworld/WalletRuntime.java). Runtime opens SQLite, constructs the ledger, registers erasure handling and releases resources on disable.
- [`PluginSettings`](../../src/de/omegazirkel/risingworld/wallet/PluginSettings.java) loads [defaults](../../src/settings.default.json). [`PluginGUI`](../../src/de/omegazirkel/risingworld/wallet/PluginGUI.java) opens the [`WalletOverlay`](../../src/de/omegazirkel/risingworld/wallet/ui/WalletOverlay.java). [`WalletCurrencyHud`](../../src/de/omegazirkel/risingworld/wallet/ui/WalletCurrencyHud.java) owns the HUD display.
- The [ledger](ledger.md) has automated tests. Lifecycle and player UI should follow the [repository validation workflow](../../AGENTS.md).
