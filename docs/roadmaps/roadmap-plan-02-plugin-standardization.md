# Roadmap Plan 02 Plugin Standardization

## Objective
Adopt Roadmap Plan 02 portfolio standards for logger naming, admin settings visibility, localized settings text, and standardized plugin info/status panels.

## Ownership
Primary repository: `rw-plugin-oz-wallet`.

Supporting repository: `rw-plugin-oz-tools`.

## Work Packages
- [x] Package 1: Collapse specialized loggers into one main Wallet logger.
- [x] Package 2: Verify every safe `settings.default.properties` key appears in the admin `PluginSettings` tab.
- [x] Package 3: Mark list/enum settings as read-only where editing is not yet supported.
- [x] Package 4: Add missing English and German i18n labels/descriptions for settings.
- [x] Package 5: Group related settings with labeled separators.
- [x] Package 6: Add Wallet info/status panel content and redirect existing info/status commands to the shared Tools panel.

## Validation Strategy
- Run Maven package and tests.
- Verify currency/default-currency settings are represented safely.
- Verify info/status panel opens from radial menu and commands.

## Progress Notes
- Package 1 is complete: Wallet settings logging now routes through the main `OZ.Wallet` logger.
- Packages 2-5 are complete for Root Step 9: Wallet admin settings cover every safe default key, grouped separators are present, and English/German setting labels are available.
- Package 6 is complete for Root Step 10: Wallet now registers a shared Tools Info/Status provider and routes `/wallet status` and `/wallet info` to the shared panel.

## Affected Repositories/Plugins
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`
