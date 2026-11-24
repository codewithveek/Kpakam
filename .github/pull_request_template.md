## What changed

<!-- One or two sentences. -->

## Feature

<!-- PRD feature IDs, e.g. F1, F8, S1. -->

## How I checked it

- [ ] `npm run check:contract`
- [ ] `npm run typecheck && npm run lint && npm test`
- [ ] `npm run test:rules` (if rules or data shapes changed)
- [ ] `npm run test:e2e -w @kpakam/web` (if a user flow changed)
- [ ] `./gradlew :core:test ktlintCheck detekt` in `native/mobile` (if Android or the contract changed)

## Screenshots

<!-- Required for UI changes: before and after, light and dark. -->

## Contract

- [ ] This changes `packages/contract`, and both apps are updated in this PR.
