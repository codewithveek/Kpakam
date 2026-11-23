import type { DayTotals } from '@/features/ledger';
import { formatNaira } from '@/shared/money/format';
import type { Kobo } from '@/shared/money/kobo';
import { MESSAGES } from '@/shared/messages/en';

function Tile({
  label,
  amount,
  testId,
  emphasis = false,
}: {
  readonly label: string;
  readonly amount: Kobo;
  readonly testId: string;
  readonly emphasis?: boolean;
}) {
  return (
    <div className={`rounded-2xl border border-border p-4 ${emphasis ? 'bg-primary text-on-primary' : 'bg-surface'}`}>
      <dt className={`text-sm ${emphasis ? 'opacity-90' : 'text-muted'}`}>{label}</dt>
      <dd className="amount mt-1 text-2xl font-bold" data-testid={testId}>
        {formatNaira(amount)}
      </dd>
    </div>
  );
}

/** F5: today's numbers, derived live from journals. */
export function TodayTotals({ totals }: { readonly totals: DayTotals }) {
  return (
    <dl className="grid grid-cols-2 gap-3">
      <Tile label={MESSAGES.dashboard.sales} amount={totals.sales} testId="total-sales" />
      <Tile label={MESSAGES.dashboard.expenses} amount={totals.expenses} testId="total-expenses" />
      <Tile label={MESSAGES.dashboard.profit} amount={totals.profit} testId="total-profit" emphasis />
      <Tile label={MESSAGES.dashboard.debtCollected} amount={totals.debtCollected} testId="total-collected" />
    </dl>
  );
}
