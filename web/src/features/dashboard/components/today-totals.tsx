import type { DayTotals } from '@/features/ledger';
import { formatNaira } from '@/shared/money/format';
import type { Kobo } from '@/shared/money/kobo';
import { MESSAGES } from '@/shared/messages/en';

function Stat({ label, amount, testId }: { readonly label: string; readonly amount: Kobo; readonly testId: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <dt className="truncate text-[13px] font-medium text-on-ink-muted">{label}</dt>
      <dd className="amount truncate text-[17px] font-semibold" data-testid={testId}>
        {formatNaira(amount)}
      </dd>
    </div>
  );
}

export interface TodayTotalsProps {
  readonly totals: DayTotals;
}

/** F5: the one black card. Profit is the single big number; sales, expenses, and debts paid back sit below. */
export function TodayTotals({ totals }: TodayTotalsProps) {
  return (
    <section aria-labelledby="hero-label" className="rounded-[32px] bg-ink p-6 text-on-ink">
      <p id="hero-label" className="text-[15px] font-medium text-on-ink-muted">
        {MESSAGES.dashboard.profitToday}
      </p>
      <p className="amount display mt-3 text-[48px] font-bold" data-testid="total-profit">
        {formatNaira(totals.profit)}
      </p>
      <dl className="mt-6 grid grid-cols-3 gap-3 border-t border-ink-line pt-5">
        <Stat label={MESSAGES.dashboard.sales} amount={totals.sales} testId="total-sales" />
        <Stat label={MESSAGES.dashboard.expenses} amount={totals.expenses} testId="total-expenses" />
        <Stat label={MESSAGES.dashboard.paidBack} amount={totals.debtCollected} testId="total-collected" />
      </dl>
    </section>
  );
}
