# Ledger contract

Version: `ledger-v1`. Both apps implement these rules and run every case in
`journal-cases.json`.

## Money

- All stored money is integer kobo. 1 naira = 100 kobo.
- Naira from a draft becomes kobo once, at the draft-to-journal boundary.
  Use decimal arithmetic, not binary floating point: take the shortest
  round-trip decimal form of the number (JS `String(n)`, Kotlin
  `BigDecimal.valueOf(n)`), multiply by 100, and round half up to an integer.
  Example: `1.005` → `101` kobo (plain `floor(1.005 * 100 + 0.5)` gives the
  wrong answer, `100`).
- A total that rounds to 0 kobo is an error (`AMOUNT_TOO_SMALL`).

## Draft to journal

Inputs: one validated draft, the resolved `customerId` (or `null`), a new
journal `id` (ULID), an `idempotencyKey`, `occurredAt`, and the `source`.

Let `T = kobo(totalNaira)` and `P = kobo(paidNaira ?? 0)`.

| Draft kind    | Condition                      | Journal kind  | Lines (in this order)                        |
| ------------- | ------------------------------ | ------------- | -------------------------------------------- |
| `sale`        | —                              | `sale`        | `cash +T`, `sales −T`                        |
| `expense`     | —                              | `expense`     | `expenses +T`, `cash −T`                     |
| `repayment`   | customer required              | `repayment`   | `cash +T`, `receivable:C −T`                 |
| `credit_sale` | customer required, `P == 0`    | `credit_sale` | `receivable:C +T`, `sales −T`                |
| `credit_sale` | customer required, `0 < P < T` | `credit_sale` | `cash +P`, `receivable:C +(T−P)`, `sales −T` |
| `credit_sale` | `P == T`                       | `sale`        | `cash +T`, `sales −T`                        |

Errors:

- `CUSTOMER_REQUIRED`: `repayment`, or `credit_sale` with `P < T`, and no customer.
- `PAID_EXCEEDS_TOTAL`: `credit_sale` with `P > T`.
- `AMOUNT_TOO_SMALL`: `T == 0` (or `P` rounds below zero, which the schema forbids).

`paidNaira` is ignored for kinds other than `credit_sale`.
The journal keeps `customerId` when a customer was resolved, even for a `sale`.

### Description

Join the non-empty parts `[quantity, unit, item]` with single spaces.
Format `quantity` without trailing zeros (`3`, `2.5`). If the result is empty,
use the kind label: `Sale`, `Expense`, `Repayment`, `Credit sale`. Trim to 120
characters.

## Reversal

A reversal copies the original lines in the same order with each sign flipped.
`kind = reversal`, `reverses = original.id`, `customerId = original.customerId`,
`description = "Reversal: " + original.description`, trimmed to 120
characters. A journal that already has a reversal cannot be reversed again
(`ALREADY_REVERSED`); a reversal journal can itself be reversed.

## Invariants (checked before every write)

- 2 to 10 lines.
- Every `amountKobo` is a non-zero integer.
- Lines sum to exactly zero.
- Accounts match `cash | sales | expenses | receivable:<ULID>`.

## Balances

- Customer balance = sum of `amountKobo` of all `receivable:<customerId>`
  lines. Positive means the customer owes the trader.
- `customers/{id}.balanceKobo` is a cache, updated with `increment()` in the
  same batch as the journal. It can be rebuilt from lines at any time.
- Day totals (Africa/Lagos day of `occurredAt`):
  - sales = − sum of `sales` lines
  - expenses = sum of `expenses` lines
  - profit = sales − expenses
  - debtCollected = − sum of `receivable:*` lines from `repayment` journals,
    plus the same for reversals of repayments (so an undone repayment nets out).
