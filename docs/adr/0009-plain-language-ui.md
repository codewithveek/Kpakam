# 0009. Plain words in the app, no technical terms

## Status

Accepted, 23 November 2026.

## Context

Traders are not engineers. Words such as "on device", "cloud", "sync", "model",
or "JSON" confuse them and reduce trust.

## Decision

- Every string the trader sees lives in one messages file per app
  (`web/src/shared/messages/en.ts`, `native/mobile/app/src/main/res/values/strings.xml`).
- The words follow the trader: "On your phone" and "Online", "Free, no data
  used", "Not sent yet", "Save data", "Who owes you", "Expenses".
- Reason labels come from the table in `packages/contract/routing.md`, so both
  apps say the same thing.
- Status never relies on colour alone: each badge has an icon and words, and
  each amount has a sign.

## Consequences

- New strings need a plain-words review. Reviewers check the messages file in
  each pull request.
- Developer documentation can still use technical terms; the app cannot.
