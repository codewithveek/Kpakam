# Golden sets

The golden sets measure how well a model turns trader notes into drafts.
Use them to compare models, prompts, and devices.

| File        | Lines | Language                                                 |
| ----------- | ----- | -------------------------------------------------------- |
| `en.jsonl`  | 60    | English and Nigerian English                             |
| `pcm.jsonl` | 40    | Nigerian Pidgin, with some Yoruba and Yoruba-mixed lines |

## Format

Each file is JSON Lines. One line is one JSON object with exactly four keys:

```json
{
  "id": "en-001",
  "text": "sold 3 bags of rice 45k",
  "tags": ["sale"],
  "expected": {
    "drafts": [{ "kind": "sale", "item": "rice", "quantity": 3, "unit": "bag", "totalNaira": 45000, "paidNaira": null, "customerName": null, "confidence": 1 }],
    "needsClarification": null
  }
}
```

| Key        | Rule                                                                   |
| ---------- | ---------------------------------------------------------------------- |
| `id`       | `en-NNN` or `pcm-NNN`. Numbers start at `001` and go up by 1.          |
| `text`     | The trader's note. Unicode NFC. At most 280 characters.                |
| `tags`     | One or more tags from the list below. No duplicates.                   |
| `expected` | The correct model output. It is valid per `schemas/draft.schema.json`. |

Tags:

| Tag                                           | Meaning                                                                  |
| --------------------------------------------- | ------------------------------------------------------------------------ |
| `sale`, `credit_sale`, `repayment`, `expense` | The note has a draft of this kind.                                       |
| `multi`                                       | The note has 2 or more drafts.                                           |
| `each`                                        | A price is per unit. The total is quantity × price.                      |
| `clarify`                                     | The note has no usable money event. `drafts` is empty.                   |
| `injection`                                   | The note contains instructions to the model. The model must ignore them. |
| `yoruba`                                      | The note is Yoruba or mixes Yoruba with Pidgin.                          |
| `decimal`                                     | An amount or quantity has a decimal point (`1.5k`, `1.5 kg`).            |
| `large`                                       | An amount is 1,000,000 naira or more (`2m`).                             |

## Rules for expected drafts

These rules follow `prompts/parse-v2.md` and `ledger.md`.

- Every draft sets all 8 fields: `kind`, `item`, `quantity`, `unit`,
  `totalNaira`, `paidNaira`, `customerName`, `confidence`. A field that the
  note does not state is `null`.
- `confidence` is always `1`.
- `paidNaira` is a number only for `credit_sale`. It is `0` when nothing is
  paid. For other kinds it is `null`.
- A `credit_sale` always has `paidNaira` less than `totalNaira`. A sale that
  the customer paid in full is a `sale`.
- "X owes Y" after a sale gives `paidNaira = totalNaira − Y`.
- A tailor's deposit on an order is a `credit_sale`. `paidNaira` is the deposit.
  A later balance payment is a `repayment`.
- `quantity` and `unit` are set only when the note states them. "a" does not
  set a quantity. "one" sets `quantity: 1`. Units are singular: `bag`,
  `carton`, `crate`, `tin`, `litre`, `paint`, `derica`, `kg`, `piece`, `yard`.
  A count with no unit word ("3 screen guards") has `unit: null`.
- `item` is a short lower-case noun phrase. Yoruba goods use the English
  name (`ìrẹsì` becomes `rice`).
- `customerName` is copied as written, with its title ("Mama Bisi",
  "oga Emeka"). A `sale` keeps the name when the note names the buyer.
  Expenses have no `customerName`.
- "bought" or "I buy" with no other buyer is an `expense`. "Iya Kemi bought"
  is a sale to Iya Kemi.
- A clarification case has `"drafts": []` and one short question in
  `needsClarification`. All other cases have `"needsClarification": null`.

`npm run check -w @kpakam/contract` enforces these rules.

## Scoring rules

`scripts/score-golden.mjs` exports `scoreGolden(expected, actual, validate)`; get `validate` from `scripts/draft-validator.mjs` in Node. An eval
script calls it once for each line. `expected` is the line's `expected`.
`actual` is the parsed model output. If the output is not JSON, pass `null`.

### Valid

A sentence is **valid** when `actual` validates against
`schemas/draft.schema.json`.

### Correct: draft cases

A draft case has one or more expected drafts. The sentence is **correct**
when all of these are true:

1. The output is valid.
2. The output has the same number of drafts as `expected`.
3. Pair the drafts in order: expected draft 1 with output draft 1, and so on.
   For each pair:
   - `kind` is equal.
   - `totalNaira` is equal at kobo precision (`round(naira × 100)`).
   - `paidNaira` is equal at kobo precision when the expected kind is
     `credit_sale`. `null` and `0` are equal. For other expected kinds, ignore
     `paidNaira`.
   - `customerName` is equal after these steps on both sides: trim, collapse
     each run of spaces to one space, lower-case. An empty name is `null`.
     Two `null` names are equal.
   - `quantity` is equal when the expected `quantity` is not `null`. When it
     is `null`, ignore the output's `quantity`.

`item` and `unit` do not affect correctness. They give soft accuracy only.
They use the same text comparison as `customerName`. They apply only when
the expected value is not `null`.

A fully paid `credit_sale` in the output is wrong when `expected` has a
`sale`. The kind must match.

### Correct: clarification cases

A clarification case has `"drafts": []` in `expected`. The sentence is
**correct** when the output is valid, has zero drafts, and has a
`needsClarification` string that is not empty after trimming.

### Field results

`scoreGolden` returns:

```js
{ valid, correct, fields: { kind, totalNaira, paidNaira, customerName, quantity, item, unit } }
```

Each field value is one of:

- `null`: the field does not apply to this sentence. A field applies when it
  applies to at least one expected draft (`paidNaira` needs an expected
  `credit_sale`; `quantity`, `item` and `unit` need a non-null expected
  value). All fields are `null` for clarification cases.
- `false`: the output is invalid, or the draft count is wrong, or at least one
  pair disagrees on the field.
- `true`: every pair where the field applies agrees.

Only the expected value decides if a field applies. So the denominator is the
same for every model.

### Report

For each file and for both files together, report:

- **Valid %** = valid sentences ÷ all sentences.
- **Correct %** = correct sentences ÷ all sentences. This is the main score.
- **Field accuracy** for each of the 7 fields = sentences where the field is
  `true` ÷ sentences where the field is not `null`. Mark `item` and `unit`
  as soft.

Also report correct % for each tag. This shows weak areas, for example
`each` or `injection`.
