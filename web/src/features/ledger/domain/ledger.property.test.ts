import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import type { Draft } from '@/inference/domain/draft';
import { newCustomerId, newUlid } from '@/shared/ids/ulid';
import { buildJournal, buildReversal } from './build-journal';
import { checkLines } from './invariants';
import type { Journal } from './journal';
import { computeBalances, computeDayTotals } from './totals';

const CUSTOMER = newCustomerId();
const SOURCE = { inference: 'manual', reason: 'MANUAL', promptVersion: null } as const;

const nairaArb = fc.integer({ min: 1, max: 10_000_000_00 }).map((kobo) => kobo / 100);
const draftArb: fc.Arbitrary<Draft> = fc
  .record({
    kind: fc.constantFrom('sale', 'credit_sale', 'repayment', 'expense' as const),
    totalNaira: nairaArb,
    paidFraction: fc.double({ min: 0, max: 1, noNaN: true }),
  })
  .map(({ kind, totalNaira, paidFraction }) => ({
    kind,
    item: 'goods',
    quantity: null,
    unit: null,
    totalNaira,
    paidNaira: kind === 'credit_sale' ? Math.floor(totalNaira * paidFraction * 100) / 100 : null,
    customerName: 'Ada',
    confidence: 1,
  }));

function journalOf(draft: Draft): Journal {
  const result = buildJournal({ draft, customerId: CUSTOMER, id: newUlid(), idempotencyKey: 'k', occurredAt: new Date(), source: SOURCE });
  if (!result.ok) throw new Error(result.error);
  return result.value;
}

function reverse(journal: Journal): Journal {
  const result = buildReversal({ original: journal, alreadyReversed: false, id: newUlid(), occurredAt: new Date() });
  if (!result.ok) throw new Error(result.error);
  return result.value;
}

describe('ledger properties', () => {
  it('every journal built from a valid draft sums to zero', () => {
    fc.assert(fc.property(draftArb, (draft) => checkLines(journalOf(draft).lines).ok));
  });

  it('a reversal nets every account to zero', () => {
    fc.assert(
      fc.property(draftArb, (draft) => {
        const journal = journalOf(draft);
        const totals = computeDayTotals([journal, reverse(journal)]);
        expect(totals.sales).toBe(0);
        expect(totals.expenses).toBe(0);
        expect(totals.debtCollected).toBe(0);
        expect(computeBalances([journal, reverse(journal)]).get(CUSTOMER) ?? 0).toBe(0);
      }),
    );
  });

  it('reversing a reversal restores the original balances', () => {
    fc.assert(
      fc.property(fc.array(draftArb, { minLength: 1, maxLength: 8 }), (drafts) => {
        const journals = drafts.map(journalOf);
        const first = journals[0];
        if (first === undefined) return;
        const undo = reverse(first);
        const redo = reverse(undo);
        const before = computeBalances(journals).get(CUSTOMER) ?? 0;
        const after = computeBalances([...journals, undo, redo]).get(CUSTOMER) ?? 0;
        expect(after).toBe(before);
        expect(computeDayTotals([...journals, undo, redo]).profit).toBe(computeDayTotals(journals).profit);
      }),
    );
  });
});
