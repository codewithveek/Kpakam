import { err, ok, type Result } from '@/shared/result';
import type { JournalLine } from './journal';

const ACCOUNT_PATTERN = /^(cash|sales|expenses|receivable:[0-9A-HJKMNP-TV-Z]{26})$/;
const MIN_LINES = 2;
const MAX_LINES = 10;

export type InvariantViolation = 'LINE_COUNT' | 'ZERO_OR_FRACTIONAL_AMOUNT' | 'UNBALANCED' | 'BAD_ACCOUNT';

/**
 * Checks the journal invariants before every write (ledger.md).
 * @returns ok, or the first violation found.
 */
export function checkLines(lines: readonly JournalLine[]): Result<true, InvariantViolation> {
  if (lines.length < MIN_LINES || lines.length > MAX_LINES) return err('LINE_COUNT');
  let sum = 0;
  for (const line of lines) {
    if (!Number.isSafeInteger(line.amountKobo) || line.amountKobo === 0) return err('ZERO_OR_FRACTIONAL_AMOUNT');
    if (!ACCOUNT_PATTERN.test(line.account)) return err('BAD_ACCOUNT');
    sum += line.amountKobo;
  }
  return sum === 0 ? ok(true) : err('UNBALANCED');
}
