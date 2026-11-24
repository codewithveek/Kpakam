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
    <div className="flex flex-col gap-5">
      <PageHeader title={MESSAGES.ledger.title} subtitle={formatLagosDate(day)} />
      <DayStrip today={today} value={day} onChange={setDay} />
      {view.entries.length > 0 && (
        <div className="grid grid-cols-2 gap-2" aria-live="polite">
          <p className="flex flex-col gap-1 rounded-[24px] bg-surface p-4">
            <span className="text-[13px] font-medium text-muted">{MESSAGES.ledger.daySales}</span>
            <span className="amount text-[22px] font-bold tracking-[-0.02em] text-income">+{formatNaira(totals.sales)}</span>
          </p>
          <p className="flex flex-col gap-1 rounded-[24px] bg-surface p-4">
            <span className="text-[13px] font-medium text-muted">{MESSAGES.ledger.dayExpenses}</span>
            <span className="amount text-[22px] font-bold tracking-[-0.02em]">−{formatNaira(totals.expenses)}</span>
          </p>
        </div>
      )}
      {!view.loading && view.entries.length === 0 && (
        <p className="rounded-[24px] bg-surface p-6 text-center text-[15px] text-muted">{MESSAGES.ledger.empty}</p>
      )}
      <JournalList entries={view.entries} reversed={view.reversed} customers={byId} label={MESSAGES.ledger.title} />
    </div>
  );
}
