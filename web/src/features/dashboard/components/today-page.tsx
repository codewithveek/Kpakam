import { Link } from '@tanstack/react-router';
import { useMemo } from 'react';
import { computeDayTotals, JournalList, useCustomers, useJournalRange } from '@/features/ledger';
import { JobList, QuickEntryPanel, useJobs } from '@/features/quick-entry';
import { MESSAGES } from '@/shared/messages/en';
import { lagosDay } from '@/shared/time/lagos';
import { PhoneIcon, SparkIcon } from '@/shared/ui/icons';
import { useDaySummary } from '../hooks/use-day-summary';
import { useWeeklyStats } from '../hooks/use-weekly-stats';
import { TodayTotals } from './today-totals';

const RECENT = 5;

/** The main screen: quick entry, drafts to check, today's numbers, latest entries. */
export function TodayPage() {
  const today = lagosDay(new Date());
  const view = useJournalRange(today, today);
  const jobs = useJobs();
  const { byId } = useCustomers();
  const totals = useMemo(() => computeDayTotals(view.entries.map((e) => e.journal)), [view.entries]);
  const summary = useDaySummary(totals);
  const stats = useWeeklyStats();
  return (
    <div className="flex flex-col gap-4">
      <QuickEntryPanel />
      <JobList jobs={jobs} />
      <section aria-labelledby="today-heading" className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between">
          <h2 id="today-heading" className="text-xl font-bold">
            {MESSAGES.dashboard.title}
          </h2>
          {stats !== null && (
            <p className="inline-flex items-center gap-1 text-sm font-semibold text-device" data-testid="calls-avoided">
              <PhoneIcon width={16} height={16} />
              {MESSAGES.inference.avoided(stats.cloudCallsAvoided)}
            </p>
          )}
        </div>
        <TodayTotals totals={totals} />
        {summary !== null && (
          <p className="flex gap-2 rounded-xl bg-surface-2 p-3">
            <SparkIcon className="shrink-0 text-accent" />
            <span>{summary}</span>
          </p>
        )}
        {!view.loading && view.entries.length === 0 && <p className="text-muted">{MESSAGES.dashboard.noEntries}</p>}
        <JournalList entries={view.entries.slice(0, RECENT)} reversed={view.reversed} customers={byId} label={MESSAGES.ledger.title} />
        {view.entries.length > RECENT && (
          <Link to="/history" className="min-h-12 self-end py-3 font-semibold text-primary">
            {MESSAGES.nav.history} →
          </Link>
        )}
      </section>
    </div>
  );
}
