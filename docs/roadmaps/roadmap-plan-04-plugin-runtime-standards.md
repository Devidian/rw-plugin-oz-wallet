# Roadmap Plan 04 Plugin Runtime Standards

## Objective
Apply Plan 04 portfolio runtime standards to Wallet without changing economy business behavior.

## Ownership
Primary repository: `rw-plugin-oz-wallet`

Supporting repositories:
- `rw-plugin-oz-tools` for shared settings, i18n, persistence, and overlay behavior.

## Dependencies
- Hard runtime dependency: `rw-plugin-oz-tools`.
- Wallet API compatibility for Shop, Marketplace, GPS, LandClaim, and Rewards must be preserved.

## Phases
- [x] Phase 1: Audit for deprecated Tools `SQLite` usage and migrate to `SQLiteConnectionFactory` if needed.
- [x] Phase 2: Verify i18n files are loaded only once during `onEnable`.
- [x] Phase 3: Add PlayerPluginSettings shortcut visibility for `/ozt open` and inventory entry, defaulting to visible.
- [x] Phase 4: Add escape-close handling for open Wallet panels where possible.
- [x] Phase 5: Verify persisted runtime data uses SQLite/world-safe storage.
- [x] Phase 6: Update README/HISTORY and validate.

## Implementation Notes
- Wallet already uses `SQLiteConnectionFactory` and world-scoped SQLite for balances, transactions, and player settings.
- Wallet loads i18n once through `I18n.getInstance(this)` during enable.
- The player settings panel now includes a default-visible Wallet shortcut setting.
- Custom-overlay Escape behavior is deferred to the future Rising World API layer.

## Risks
- Wallet is an integration dependency; UI/settings cleanup must not break public Wallet API or transaction behavior.

## Validation Strategy
- Run `mvn -B test` and `mvn -B -DskipTests package`.
- Run compatibility checks for Shop and Marketplace after Wallet changes.

## Affected Repositories/Plugins
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Keep changes limited to runtime standards and UI behavior. Avoid API signature changes.
