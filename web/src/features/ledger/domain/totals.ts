import { asKobo, type Kobo } from '@/shared/money/kobo';
import { customerOfAccount, type Journal } from './journal';

/** Today dashboard numbers (ledger.md "Balances"). */
export interface DayTotals {
  readonly sales: Kobo;
  readonly expenses: Kobo;
  readonly profit: Kobo;
  readonly debtCollected: Kobo;
  readonly entries: number;
}

function isRepaymentLike(journal: Journal, byId: ReadonlyMap<string, Journal>): boolean {
  if (journal.kind === 'repayment') return true;
  return journal.kind === 'reversal' && journal.reverses !== null && byId.get(journal.reverses)?.kind === 'repayment';
}

/**
 * Derives the day totals from journals. Pure; never reads stored totals.
 * @param journals - The day's journals.
 * @param lookup - Journals that reversals may point to (defaults to `journals`).
 */
export function computeDayTotals(journals: readonly Journal[], lookup: readonly Journal[] = journals): DayTotals {
  const byId = new Map(lookup.map((j) => [j.id as string, j]));
  let sales = 0;
  let expenses = 0;
  let collected = 0;
  for (const journal of journals) {
    const repaymentLike = isRepaymentLike(journal, byId);
    for (const { account, amountKobo } of journal.lines) {
      if (account === 'sales') sales -= amountKobo;
      else if (account === 'expenses') expenses += amountKobo;
      else if (repaymentLike && customerOfAccount(account) !== null) collected -= amountKobo;
    }
  }
  return {
    sales: asKobo(sales),
    expenses: asKobo(expenses),
    profit: asKobo(sales - expenses),
    debtCollected: asKobo(collected),
    entries: journals.filter((j) => j.kind !== 'reversal').length,
  };
}

/** Rebuilds every customer's balance from journal lines. Positive = they owe the trader. */
export function computeBalances(journals: readonly Journal[]): ReadonlyMap<string, Kobo> {
  const balances = new Map<string, number>();
  for (const journal of journals) {
    for (const { account, amountKobo } of journal.lines) {
      const customerId = customerOfAccount(account);
      if (customerId !== null) balances.set(customerId, (balances.get(customerId) ?? 0) + amountKobo);
    }
  }
  return new Map([...balances].map(([id, total]) => [id, asKobo(total)]));
}

/** Returns the set of journal ids that have been reversed. */
export function reversedIds(journals: readonly Journal[]): ReadonlySet<string> {
  return new Set(journals.flatMap((j) => (j.reverses === null ? [] : [j.reverses])));
}
