# 0005. One contract package for both apps

## Status

Accepted, 23 November 2026.

## Context

Two apps in two languages (TypeScript and Kotlin) write the same Firestore data
and must make the same routing and money decisions. Rules written twice drift.

## Decision

`packages/contract` is the single source of truth:

- JSON Schemas for drafts and journals.
- Written specs (`routing.md`, `ledger.md`) plus test cases
  (`routing-cases.json`, `journal-cases.json`, `language-cases.json`).
- Versioned prompts and Remote Config defaults.
- Golden sets for evaluation (60 English, 40 Pidgin sentences).

Both apps read these files in their tests. The web parity test checks its Zod
schema against the JSON Schema. `npm run check:contract` validates the package.

## Consequences

- A rule change is one pull request that updates the spec, the cases, and both
  apps. CI fails if either app disagrees with the cases.
- The Android build copies prompts and Remote Config defaults from the package
  at build time, so no copies live in `native/mobile`.
