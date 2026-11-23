import type { DayTotals } from '@/features/ledger';
import { formatNaira } from '@/shared/money/format';
import type { Kobo } from '@/shared/money/kobo';
import { MESSAGES } from '@/shared/messages/en';
import { PhoneIcon } from '@/shared/ui/icons';

function Stat({ label, amount, testId }: { readonly label: string; readonly amount: Kobo; readonly testId: string }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
      <dt className="text-[13px] font-medium text-white/75">{label}</dt>
      <dd className="amount truncate text-[17px] font-semibold text-white" data-testid={testId}>
        {formatNaira(amount)}
      </dd>
    </div>
  );
}

export interface TodayTotalsProps {
  readonly totals: DayTotals;
  readonly cloudCallsSaved: number | null;
}

/** F5 hero: one big number (profit), three small ones, on a solid brand surface (no gradient behind numbers). */
export function TodayTotals({ totals, cloudCallsSaved }: TodayTotalsProps) {
  return (
    <section aria-labelledby="hero-label" className="rounded-[24px] bg-primary p-5 text-white shadow-float dark:bg-[var(--hero-from)]">
      <p id="hero-label" className="text-[15px] font-medium text-white/80">
        {MESSAGES.dashboard.profitToday}
      </p>
      <p className="amount mt-1 text-[40px] font-bold leading-none tracking-[-0.03em]" data-testid="total-profit">
        {formatNaira(totals.profit)}
      </p>
      <dl className="mt-5 flex gap-4 border-t border-white/15 pt-4">
        <Stat label={MESSAGES.dashboard.sales} amount={totals.sales} testId="total-sales" />
        <Stat label={MESSAGES.dashboard.expenses} amount={totals.expenses} testId="total-expenses" />
        <Stat label={MESSAGES.dashboard.debtCollected} amount={totals.debtCollected} testId="total-collected" />
      </dl>
      {cloudCallsSaved !== null && (
        <p
          className="mt-4 inline-flex min-h-8 items-center gap-1.5 rounded-full bg-white/12 px-3 text-[13px] font-semibold text-white"
          data-testid="calls-avoided"
        >
          <PhoneIcon width={15} height={15} />
          <span>{MESSAGES.inference.avoided(cloudCallsSaved)}</span>
        </p>
      )}
    </section>
  );
}
