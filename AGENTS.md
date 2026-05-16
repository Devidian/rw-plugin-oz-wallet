# AGENTS.md

## Repository Purpose

This repository owns the `OZ - Wallet` Rising World plugin.

## Ownership

Owns:
- wallet and economy business logic
- currency registration
- player balances
- transaction history
- standalone wallet UI
- public wallet API for optional plugin integrations

Does not own:
- generic UI, settings, persistence, logging, or transport helpers
- feature-specific spending rules for GPS, land claim, Discord, admin-utils, or intercom
- reusable infrastructure that belongs in `rw-plugin-oz-tools`

## Dependencies

- Hard runtime dependency: `rw-plugin-oz-tools`
- No required v1 dependency on GPS, Discord, Land Claim, Admin Utils, or Global Intercom

## Mandatory Workflow Rules

- Preserve the Java 20 baseline.
- Keep wallet economy state local to this plugin.
- Use `rw-plugin-oz-tools` helpers for shared runtime concerns.
- Do not put the wallet overview into `PlayerPluginSettingsOverlay`.
- Treat API result objects and the four public main-class methods as the compatibility surface for sibling plugins.
- Do not expose `WalletService` or database internals as public integration API.
- Keep `README.md`, `HISTORY.md`, and `PLANS.md` aligned with behavior changes.

## Validation

- Run `mvn -B -DskipTests package` for build-impacting changes.
- Run `scripts/verify-plugin-api.sh --summary` plus targeted `--class` or `--method` checks when adding or changing Rising World API calls.
- Run `mvn -B test` when tests exist.
