# Admin Wallet Overview

## Objective

Show the wallet transaction timestamp timezone explicitly and give admins a compact global wallet overview.

## Ownership

- Affected plugin: `rw-plugin-oz-wallet`
- Required dependency: `rw-plugin-oz-tools`
- Template impact: none, because this is feature UI and wallet persistence behavior only

## Checklist

- [x] Render transaction dates as GMT and label the date column accordingly.
- [x] Add an admin-only latest global transactions tab with player names.
- [x] Add an admin-only global balances tab grouped by currency.
- [x] Update i18n and README/HISTORY documentation.
- [x] Validate with Maven package and PluginAPI checks.

## Risks

- Player names depend on Rising World last-known-player lookup; unknown names fall back to the player database id.
- Admin visibility is controlled by `Player.isAdmin()`.

## Rollback

Revert the Wallet UI, service, database query, i18n, and documentation changes in this task. No schema migration is introduced.
