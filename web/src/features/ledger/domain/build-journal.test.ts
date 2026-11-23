import journalCases from '@contract/journal-cases.json';
import { describe, expect, it } from 'vitest';
import { draftSchema, type Draft } from '@/inference/domain/draft';
import { domainProblems } from '@/inference/domain/draft-validation';
import { parseCustomerId, parseUlid, type Ulid } from '@/shared/ids/ulid';
import { buildJournal, buildReversal, describeDraft } from './build-journal';
import { checkLines } from './invariants';

const CUSTOMER = parseCustomerId(journalCases.customerId);
const ID = parseUlid('01JDX7Q3M8ZZZZZZZZZZZZZZZZ') as Ulid;
const AT = new Date('2026-11-24T08:41:00Z');
const SOURCE = { inference: 'on_device', reason: 'DEFAULT_DEVICE', promptVersion: 'parse-v1' } as const;

function toDraft(raw: unknown): Draft {
  const d = draftSchema.parse(raw);
  return {
    kind: d.kind,
    item: d.item ?? null,
    quantity: d.quantity ?? null,
    unit: d.unit ?? null,
    totalNaira: d.totalNaira,
    paidNaira: d.paidNaira ?? null,
    customerName: d.customerName ?? null,
    confidence: d.confidence,
  };
}

function build(draft: Draft, withCustomer: boolean) {
  return buildJournal({ draft, customerId: withCustomer ? CUSTOMER : null, id: ID, idempotencyKey: 'k1', occurredAt: AT, source: SOURCE });
}

describe('contract: journal-cases.json', () => {
  it.each(journalCases.cases)('$name', (testCase) => {
    const result = build(toDraft(testCase.draft), testCase.customer);
    const { expected } = testCase;
    if ('error' in expected) {
      expect(result).toEqual({ ok: false, error: expected.error });
      return;
    }
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    const lines = result.value.lines.map((l) => [l.account.startsWith('receivable:') ? 'receivable' : l.account, l.amountKobo]);
    expect(result.value.kind).toBe(expected.kind);
    expect(result.value.description).toBe(expected.description);
    expect(lines).toEqual(expected.lines);
    expect(checkLines(result.value.lines).ok).toBe(true);
  });
});

describe('buildJournal', () => {
  it('stamps Lagos day and offset time', () => {
    const result = build(toDraft({ kind: 'sale', totalNaira: 100, confidence: 1 }), false);
    expect(result.ok && result.value.occurredAt).toBe('2026-11-24T09:41:00+01:00');
    expect(result.ok && result.value.day).toBe('2026-11-24');
  });

  it('uses the Lagos day across UTC midnight', () => {
    const late = buildJournal({
      draft: toDraft({ kind: 'sale', totalNaira: 1, confidence: 1 }),
      customerId: null,
      id: ID,
      idempotencyKey: 'k',
      occurredAt: new Date('2026-11-24T23:30:00Z'),
      source: SOURCE,
    });
    expect(late.ok && late.value.day).toBe('2026-11-25');
  });

  it('trims long descriptions to 120 characters', () => {
    const draft = toDraft({ kind: 'sale', item: 'x'.repeat(80), quantity: 1, unit: 'y'.repeat(30), totalNaira: 1, confidence: 1 });
    expect(describeDraft(draft).length).toBeLessThanOrEqual(120);
  });

  it('flags customer and paid problems before building', () => {
    expect(domainProblems(toDraft({ kind: 'repayment', totalNaira: 5, confidence: 1 }), 0)).toHaveLength(1);
    expect(domainProblems(toDraft({ kind: 'credit_sale', totalNaira: 5, paidNaira: 6, customerName: 'A', confidence: 1 }), 0)).toHaveLength(1);
  });
});

describe('buildReversal', () => {
  const original = build(toDraft(journalCases.cases[2]?.draft), true);

  it('flips every line and links the original', () => {
    if (!original.ok) throw new Error('fixture');
    const result = buildReversal({ original: original.value, alreadyReversed: false, id: ID, occurredAt: AT });
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.kind).toBe('reversal');
    expect(result.value.reverses).toBe(original.value.id);
    expect(result.value.lines.map((l) => l.amountKobo)).toEqual(original.value.lines.map((l) => 0 - l.amountKobo));
    expect(result.value.customerId).toBe(original.value.customerId);
    expect(result.value.description).toBe('Reversal: 3 bag rice');
  });

  it('refuses to reverse twice', () => {
    if (!original.ok) throw new Error('fixture');
    expect(buildReversal({ original: original.value, alreadyReversed: true, id: ID, occurredAt: AT })).toEqual({ ok: false, error: 'ALREADY_REVERSED' });
  });
});
