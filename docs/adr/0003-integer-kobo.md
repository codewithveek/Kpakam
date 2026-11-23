# 0003. All money is integer kobo

## Status

Accepted, 23 November 2026.

## Context

Floating-point naira values give wrong totals (`0.1 + 0.2`). Traders must be
able to trust every total ("kpakam" means exactly right).

## Decision

- Store and add money only as integer kobo (1 naira = 100 kobo), with a branded
  `Kobo` type on the web and a value class on Android.
- Convert from the model's naira once, at the draft-to-journal boundary, with
  decimal arithmetic and round half up (`ledger.md`). Example: 1.005 naira is
  101 kobo, not 100.
- Firestore rules require `amountKobo` to be an integer.

## Consequences

- Totals are exact. Property tests check that every journal sums to zero.
- Display code must format kobo as naira. `formatNaira` does this with
  `Intl.NumberFormat('en-NG')`.
