import { describe, expect, it } from 'vitest';
import { asKobo } from '@/shared/money/kobo';
import { checkLines } from './invariants';
import type { JournalLine } from './journal';

const C = 'receivable:01JDX6M8Z9Q4W2E3R5T7Y9V1K0' as const;
const l = (account: JournalLine['account'], amount: number): JournalLine => ({ account, amountKobo: amount as ReturnType<typeof asKobo> });

describe('checkLines', () => {
  it.each([
    ['balanced', [l('cash', 100), l('sales', -100)], { ok: true, value: true }],
    ['balanced with a receivable', [l('cash', 60), l(C, 40), l('sales', -100)], { ok: true, value: true }],
    ['one line', [l('cash', 100)], { ok: false, error: 'LINE_COUNT' }],
    ['eleven lines', Array.from({ length: 11 }, (_, i) => l('cash', i === 10 ? -10 : 1)), { ok: false, error: 'LINE_COUNT' }],
    ['zero amount', [l('cash', 0), l('sales', 0)], { ok: false, error: 'ZERO_OR_FRACTIONAL_AMOUNT' }],
    ['fractional amount', [l('cash', 0.5), l('sales', -0.5)], { ok: false, error: 'ZERO_OR_FRACTIONAL_AMOUNT' }],
    ['unknown account', [l('bank' as JournalLine['account'], 100), l('sales', -100)], { ok: false, error: 'BAD_ACCOUNT' }],
    ['bad receivable id', [l('receivable:bisi', 100), l('sales', -100)], { ok: false, error: 'BAD_ACCOUNT' }],
    ['unbalanced', [l('cash', 100), l('sales', -99)], { ok: false, error: 'UNBALANCED' }],
  ])('%s', (_name, lines, expected) => {
    expect(checkLines(lines)).toEqual(expected);
  });
});
