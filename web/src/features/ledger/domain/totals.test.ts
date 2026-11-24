import { describe, expect, it } from 'vitest';
import { newCustomerId, newUlid } from '@/shared/ids/ulid';
import type { Draft } from '@/inference/domain/draft';
import { buildJournal, buildReversal } from './build-journal';
import { journalToDraft } from './journal-from-draft';
import type { Journal } from './journal';
import { computeBalances, computeDayTotals, reversedIds } from './totals';

const C = newCustomerId();
const SOURCE = { inference: 'manual', reason: 'MANUAL', promptVersion: null } as const;
const base = { item: 'rice', quantity: null, unit: null, customerName: 'Ada', confidence: 1, paidNaira: null } as const;

function j(draft: Draft): Journal {
  const r = buildJournal({ draft, customerId: C, id: newUlid(), idempotencyKey: 'k', occurredAt: new Date(), source: SOURCE });
  if (!r.ok) throw new Error(r.error);
  return r.value;
}

describe('computeDayTotals', () => {
  const sale = j({ ...base, kind: 'sale', totalNaira: 1000 });
  const credit = j({ ...base, kind: 'credit_sale', totalNaira: 45000, paidNaira: 35000 });
  const expense = j({ ...base, kind: 'expense', totalNaira: 300 });
  const repay = j({ ...base, kind: 'repayment', totalNaira: 4000 });

  it('derives sales, expenses, profit, and debt collected', () => {
    const totals = computeDayTotals([sale, credit, expense, repay]);
    expect(totals).toEqual({ sales: 4_600_000, expenses: 30_000, profit: 4_570_000, debtCollected: 400_000, entries: 4 });
  });

  it('nets an undone repayment out of debt collected', () => {
    const undo = buildReversal({ original: repay, alreadyReversed: false, id: newUlid(), occurredAt: new Date() });
    if (!undo.ok) throw new Error('fixture');
    expect(computeDayTotals([repay, undo.value]).debtCollected).toBe(0);
    expect(computeDayTotals([undo.value], [repay, undo.value]).debtCollected).toBe(-400_000);
    expect(reversedIds([repay, undo.value]).has(repay.id)).toBe(true);
  });

  it('derives balances from receivable lines', () => {
    expect(computeBalances([credit, repay]).get(C)).toBe(600_000);
    expect(computeDayTotals([]).entries).toBe(0);
  });
});

describe('journalToDraft', () => {
  it.each([
    [{ ...base, kind: 'sale' as const, totalNaira: 1000 }, 1000, null],
    [{ ...base, kind: 'credit_sale' as const, totalNaira: 45000, paidNaira: 35000 }, 45000, 35000],
    [{ ...base, kind: 'credit_sale' as const, totalNaira: 500, paidNaira: 0 }, 500, 0],
    [{ ...base, kind: 'expense' as const, totalNaira: 300 }, 300, null],
    [{ ...base, kind: 'repayment' as const, totalNaira: 4000 }, 4000, null],
  ])('round-trips %s', (draft, total, paid) => {
    const back = journalToDraft(j(draft), 'Ada');
    expect(back?.kind).toBe(draft.kind);
    expect(back?.totalNaira).toBe(total);
    expect(back?.paidNaira).toBe(paid);
  });

  it('returns null for reversals', () => {
    const credit = j({ ...base, kind: 'sale', totalNaira: 1 });
    const undo = buildReversal({ original: credit, alreadyReversed: false, id: newUlid(), occurredAt: new Date() });
    expect(undo.ok && journalToDraft(undo.value, null)).toBeNull();
  });
});
