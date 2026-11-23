import { useState } from 'react';
import { useCustomers } from '../hooks/use-customers';
import { MESSAGES } from '@/shared/messages/en';
import { addDays, formatLagosDate, lagosDay } from '@/shared/time/lagos';
import { Button } from '@/shared/ui/button';
import { ChevronLeftIcon, ChevronRightIcon } from '@/shared/ui/icons';
import { useJournalRange } from '../hooks/use-journals';
import { JournalList } from './journal-list';

/** History: one day at a time, newest first, with Undo and Fix on each entry. */
export function HistoryPage() {
  const today = lagosDay(new Date());
  const [day, setDay] = useState(today);
  const view = useJournalRange(day, day);
  const { byId } = useCustomers();
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <Button variant="ghost" icon={<ChevronLeftIcon />} onClick={() => setDay(addDays(day, -1))} aria-label={MESSAGES.ledger.previousDay} />
        <h1 className="text-xl font-bold" aria-live="polite">
          {day === today ? MESSAGES.dashboard.title : formatLagosDate(day)}
        </h1>
        <Button
          variant="ghost"
          icon={<ChevronRightIcon />}
          onClick={() => setDay(addDays(day, 1))}
          disabled={day >= today}
          aria-label={MESSAGES.ledger.nextDay}
        />
      </div>
      {!view.loading && view.entries.length === 0 && <p className="text-muted">{MESSAGES.ledger.empty}</p>}
      <JournalList entries={view.entries} reversed={view.reversed} customers={byId} label={MESSAGES.ledger.title} />
    </div>
  );
}
