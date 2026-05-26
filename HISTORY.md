# History / Changelog / Commitlog

<https://www.conventionalcommits.org/en/v1.0.0/>

## [unreleased]

- feat: expose public Wallet currency-list API for sibling plugin dropdowns and bridge integrations
- feat: add shared Tools Info/Status panel content for Wallet and route info/status commands to it
- feat: expose default currency settings with grouped admin metadata and i18n labels
- refactor: route Wallet settings logging through the main `OZ.Wallet` logger
- feat: add admin top-20 positive standard-currency balance ranking with shared player-name lookup fallback
- feat: extend the inventory wallet panel to show up to five currencies sorted by descending balance
- fix: hide inventory wallet panel scrollbars

## [0.2.0] - 2026-05-18 | Admin wallet overview and inventory wallet panel

- feat: add GMT timestamp labels plus admin-only global transaction and world-balance wallet tabs
- feat: move the default-currency wallet display into an inventory-only side panel while keeping the existing player setting key
- feat: register the Wallet player data tab through OZ Tools `0.18.0`
- fix: restore colored one-line plugin welcome message
- fix: align the inventory wallet side panel with the top edge of the inventory background
- fix: raise the default welcome bonus to 100 and show more rows in wallet tables
- fix: trim wallet table height and make the admin audit-log row limit configurable

## [0.1.0] - 2026-05-15

- feat: create `OZ - Wallet` plugin from `rw-plugin-maven-template`
- feat: add currency registry, balances, transaction history, and public wallet API
- feat: add standalone `/wallet` UI with balances and latest transactions tabs
- feat: add default `OZC` currency settings and icon asset
- feat: widen wallet UI, localize labels, add balance cards, and use a dedicated radial wallet icon
- feat: add wallet player HUD setting, welcome toggle, default-currency API helpers, and AssetManager-backed wallet icons
- feat: add configurable one-time welcome bonus in the default currency for new players
- feat: show and update the default-currency HUD when the player wallet HUD setting is enabled
