import { useMemo, useState } from 'react';
import { formatNaira } from '@/shared/money/format';
import { MESSAGES } from '@/shared/messages/en';
import { formatLagosDate, lagosDay } from '@/shared/time/lagos';
import { PageHeader } from '@/shared/ui/page-header';
import { computeDayTotals } from '../domain/totals';
import { useCustomers } from '../hooks/use-customers';
import { useJournalRange } from '../hooks/use-journals';
import { DayStrip } from './day-strip';
import { JournalList } from './journal-list';

/** History: one day at a time, newest first. Tap an entry to Undo or Fix it. */
export function HistoryPage() {
  const today = lagosDay(new Date());
  const [day, setDay] = useState(today);
  const view = useJournalRange(day, day);
  const { byId } = useCustomers();
  const totals = useMemo(() => computeDayTotals(view.entries.map((e) => e.journal)), [view.entries]);
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={MESSAGES.ledger.title} eyebrow={formatLagosDate(day)} />
      <DayStrip today={today} value={day} onChange={setDay} />
      {view.entries.length > 0 && (
        <p className="amount px-1 text-[15px] text-muted" aria-live="polite">
          {MESSAGES.ledger.daySales} <span className="font-semibold text-income">+{formatNaira(totals.sales)}</span> · {MESSAGES.ledger.dayExpenses}{' '}
          <span className="font-semibold text-text">−{formatNaira(totals.expenses)}</span>
        </p>
      )}
      {!view.loading && view.entries.length === 0 && (
        <p className="rounded-[16px] border border-dashed border-border p-5 text-center text-muted">{MESSAGES.ledger.empty}</p>
      )}
      <JournalList entries={view.entries} reversed={view.reversed} customers={byId} label={MESSAGES.ledger.title} />
    </div>
  );
}
