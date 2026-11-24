# 0006. Every AI request is a stored job first

## Status

Accepted, 23 November 2026.

## Context

A trader can close the app, lose network, or hit data-saver in the middle of a
request. A note, photo, or voice clip must never be lost.

## Decision

- Each submission is stored in IndexedDB (Room on Android) before it runs, with
  a ULID and an idempotency key.
- A job follows a strict state machine: `queued → running → succeeded | failed`,
  plus `running → queued` (interrupted), `failed → queued` (retry), and
  cancellation. Illegal transitions throw.
- Queued jobs run again when the network returns, when the app becomes visible,
  or when the trader taps "Send now".
- A finished job stores one journal id per draft, so saving twice writes once.

## Consequences

- Offline capture works for text, photos, and voice.
- The queue must re-route jobs when they drain, because conditions change.
- Android and the web differ in one detail: Android moves a waiting job to
  cancelled through `running`; the web allows `queued → cancelled` directly.
