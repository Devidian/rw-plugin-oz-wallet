# Wallet Roadmap Plan 01

## Objective
Add the Wallet-specific UI improvements from Roadmap Plan 01 while preserving Wallet as the owner of currency state and public economy API.

## Ownership
Primary repository: `rw-plugin-oz-wallet`.

Supporting repositories:
- `rw-plugin-oz-tools` for shared settings reload/admin settings tab adoption.
- Consuming plugins use Wallet's public API but do not own Wallet UI.

## Dependencies
- Hard dependency: `rw-plugin-oz-tools`.
- No hard dependencies on GPS, LandClaim, Shop, Marketplace, or Admin Utils.

## Confirmed Decisions
- All prices and balances used by the Roadmap Plan 01 economy features are whole integers.
- Wallet remains the required economy backend for Shop and Marketplace functionality.
- Standard currency means the currently configured default currency.
- Top-20 rankings include only positive balances.
- Admin top-20 should use the shared player lookup helper and fall back to database ids for unknown players.

## Work Packages
- [ ] Package 1: Adopt shared Tools settings reload/admin settings tab metadata after the Tools baseline exists.
- [x] Package 2: Add admin-only `Spielervermoegen` tab or adapt existing admin balance tab to list the top 20 richest players by standard currency.
- [x] Package 3: Add inventory wallet display for up to five currencies sorted by descending balance.
- [x] Package 4: Keep default-currency display compact when only one currency exists.
- [x] Package 5: Update public documentation to clarify standard currency, top-player sorting, and inventory display limits.

## Risks
- Wallet already has admin transaction and world-balance views; avoid duplicating the same data under a new tab if the existing UI can be extended.
- Inventory display must remain compact and not overlap the game inventory.
- Sorting by standard currency requires a clear definition of the current default currency after settings reload.

## Validation Strategy
- Verify top 20 player list with more than and fewer than 20 players.
- Verify unknown player names fall back consistently to player database ids.
- Verify inventory panel sorts multiple currencies by balance and caps at five entries.
- Verify settings reload can change default currency metadata without corrupting balances.

## Affected Repositories/Plugins
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Rollback is UI-only if no schema change is introduced. Existing balances, currencies, and transactions must remain untouched.

## Open Questions
- None.
