# Roadmap Plan 03 Wallet Bridge API

## Objective
Expose a complete, additive Wallet API for consuming plugins and define the canonical WalletBridge behavior needed by Marketplace, Shop, Rewards, GPS, LandClaim, and future plugins.

## Ownership
Primary repository: `rw-plugin-oz-wallet`

Supporting repositories:
- `rw-plugin-maven-template` for the canonical bridge scaffold.
- Consuming plugins for migration to the finalized bridge shape.

## Dependencies
- Hard runtime dependency: `rw-plugin-oz-tools`.
- Consuming plugins must keep Wallet optional where their feature is optional and must disable economy behavior when Wallet is unavailable.

## Phases
- [x] Phase 1: Add an additive public API for listing all registered currencies with identifier, display name, icon, owner/source plugin, and default-currency marker.
- [x] Phase 2: Document the canonical bridge method set: availability, default currency, list currencies, register currency, deposit, withdraw, balance, default deposit/withdraw/balance, result success, result message, and currency metadata parsing.
- [x] Phase 3: Define compatibility expectations for reflection signatures and return-object fields so consuming bridges do not depend on service/database internals.
- [x] Phase 4: Add README/HISTORY documentation for currency listing and bridge usage.
- [x] Phase 5: Validate Wallet build/tests and run compatibility checks against Shop and Marketplace bridge callers.

## Risks
- Returning mutable internal currency objects would leak Wallet internals; API results should be public DTO-style data only.
- Consuming plugins may already parse result fields differently; the plan must preserve existing public result fields.
- Currency identifiers must remain normalized consistently so Marketplace dropdown values match transaction calls.

## Validation Strategy
- Run `scripts/verify-plugin-api.sh --summary` if API usage changes.
- Run `mvn -B -DskipTests package`.
- Run `mvn -B test`.
- Smoke-check Marketplace and Shop compile paths after they consume the new bridge shape.

## Affected Repositories/Plugins
- `rw-plugin-oz-wallet`
- `rw-plugin-maven-template`
- `rw-plugin-oz-marketplace`
- `rw-plugin-oz-shop`
- Optional later consumers: `rw-plugin-oz-gps`, `rw-plugin-oz-land-claim`, `rw-plugin-oz-rewards`

## Rollback Considerations
Keep all Wallet API changes additive. If a consuming plugin migration fails, it can keep its existing local bridge while Wallet still exposes the previous public methods.

## Progress Notes
- Phase 1-4 complete: Wallet now exposes `listCurrencies()` on the main plugin class and returns `WalletCurrenciesResult` with public `success`, `errorCode`, `message`, and `currencies` fields. The result list contains public `WalletCurrency` objects ordered with the default currency first.
- Phase 5 complete: `mvn -B test` passed for Wallet, Shop, and Marketplace.
