# 0004. Journals never change; corrections are reversals

## Status

Accepted, 23 November 2026.

## Context

If an entry can be edited or deleted, the trader cannot trust the history, and
a sync conflict can silently change money. Traders still make mistakes and must
be able to fix them.

## Decision

- Every confirmed entry is one double-entry journal whose lines sum to zero.
- Journals are create-only. The Firestore rules deny update and delete (except
  during account deletion).
- "Undo" writes a reversal: the same lines with flipped signs, linked by
  `reverses`. "Fix" writes a reversal and a corrected journal in one batch.
- Customer balances are derived from receivable lines. The cached
  `balanceKobo` is updated in the same batch and can be rebuilt at any time.

## Consequences

- History shows the original, the undo, and the fix. Nothing disappears.
- A reversal of a reversal restores the balances (property-tested).
- Lists must hide or mark reversed entries, and day totals must count reversals.
