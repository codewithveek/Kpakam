// Scoring rules for the golden sets. golden/README.md describes them in words.
// Pure: no Node or browser APIs, so the cloud eval (Node) and the on-device eval
// (Chrome) share it. Get a schema validator from draft-validator.mjs in Node.

/** Fields that decide whether a sentence is correct. */
export const HARD_FIELDS = ['kind', 'totalNaira', 'paidNaira', 'customerName', 'quantity'];
/** Fields reported as soft accuracy only. They never affect correctness. */
export const SOFT_FIELDS = ['item', 'unit'];

const kobo = (n) => Math.round(n * 100);

function normText(s) {
  if (typeof s !== 'string') return null;
  const t = s.trim().replace(/\s+/g, ' ').toLowerCase();
  return t === '' ? null : t;
}

function sameText(a, b) {
  return normText(a) === normText(b);
}

function sameMoney(a, b) {
  return typeof a === 'number' && typeof b === 'number' && kobo(a) === kobo(b);
}

// For one pair: null = field not applicable, else true/false.
const PAIR_RULES = {
  kind: (e, a) => e.kind === a.kind,
  totalNaira: (e, a) => sameMoney(e.totalNaira, a.totalNaira),
  paidNaira: (e, a) => (e.kind === 'credit_sale' ? sameMoney(e.paidNaira ?? 0, a.paidNaira ?? 0) : null),
  customerName: (e, a) => sameText(e.customerName, a.customerName),
  quantity: (e, a) => (e.quantity == null ? null : typeof a.quantity === 'number' && a.quantity === e.quantity),
  item: (e, a) => (normText(e.item) == null ? null : sameText(e.item, a.item)),
  unit: (e, a) => (normText(e.unit) == null ? null : sameText(e.unit, a.unit)),
};

// A field is applicable to a sentence when the expected drafts make it
// applicable for at least one draft. Applicability depends on `expected` only.
const APPLICABLE = {
  kind: () => true,
  totalNaira: () => true,
  customerName: () => true,
  paidNaira: (e) => e.kind === 'credit_sale',
  quantity: (e) => e.quantity != null,
  item: (e) => normText(e.item) != null,
  unit: (e) => normText(e.unit) != null,
};

/**
 * Scores one model output against one golden `expected` value.
 *
 * - valid: the output validates against schemas/draft.schema.json.
 * - correct: see golden/README.md. Draft cases need the same draft count and,
 *   pair by pair in order, equal kind, totalNaira, paidNaira (credit_sale
 *   only, null == 0), customerName (case-insensitive, trimmed, spaces
 *   collapsed, null == null) and quantity (when expected is non-null).
 *   Clarification cases need zero drafts and a non-empty needsClarification.
 * - fields: per field, true when every applicable pair agrees, false when one
 *   pair disagrees or the output is invalid or has the wrong draft count,
 *   null when the field does not apply to this sentence.
 *
 * @param {{drafts: object[], needsClarification?: string|null}} expected Golden expected value.
 * @param {unknown} actual Parsed model output (any value).
 * @param {(data: unknown) => boolean} validate Schema validator (draft-validator.mjs in Node).
 * @returns {{valid: boolean, correct: boolean, fields: Record<string, boolean|null>}}
 */
export function scoreGolden(expected, actual, validate) {
  const valid = validate(actual) === true;
  const exp = expected.drafts;
  const fields = {};
  for (const f of [...HARD_FIELDS, ...SOFT_FIELDS]) fields[f] = null;

  if (exp.length === 0) {
    const correct = valid && actual.drafts.length === 0 && normText(actual.needsClarification ?? null) != null;
    return { valid, correct, fields };
  }

  const act = valid ? actual.drafts : null;
  const countOk = act != null && act.length === exp.length;
  for (const f of Object.keys(fields)) {
    if (!exp.some((e) => APPLICABLE[f](e))) continue;
    if (!countOk) {
      fields[f] = false;
      continue;
    }
    fields[f] = exp.every((e, i) => PAIR_RULES[f](e, act[i]) !== false);
  }
  const correct = countOk && HARD_FIELDS.every((f) => fields[f] !== false);
  return { valid, correct, fields };
}
