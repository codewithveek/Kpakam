import { Link } from '@tanstack/react-router';
import { useMemo } from 'react';
import { computeDayTotals, JournalList, useCustomers, useJournalRange } from '@/features/ledger';
import { JobList, QuickEntryPanel, useJobs } from '@/features/quick-entry';
import { MESSAGES } from '@/shared/messages/en';
import { formatLagosDate, lagosDay } from '@/shared/time/lagos';
import { SparkIcon } from '@/shared/ui/icons';
import { SectionHeader } from '@/shared/ui/list';
import { PageHeader } from '@/shared/ui/page-header';
import { useDaySummary } from '../hooks/use-day-summary';
import { useWeeklyStats } from '../hooks/use-weekly-stats';
import { TodayTotals } from './today-totals';

const RECENT = 5;

/** The main screen: today's numbers, quick entry, drafts to check, latest entries. */
export function TodayPage() {
  const today = lagosDay(new Date());
  const view = useJournalRange(today, today);
  const jobs = useJobs();
  const { byId } = useCustomers();
  const totals = useMemo(() => computeDayTotals(view.entries.map((e) => e.journal)), [view.entries]);
  const summary = useDaySummary(totals);
  const stats = useWeeklyStats();
  return (
    <div className="flex flex-col gap-5">
      <PageHeader eyebrow={formatLagosDate(today)} title={MESSAGES.greeting(new Date().getHours())} />
      <TodayTotals totals={totals} cloudCallsSaved={stats?.cloudCallsAvoided ?? null} />
      <QuickEntryPanel />
      <JobList jobs={jobs} />
      <section aria-labelledby="recent-heading" className="flex flex-col gap-2">
        <SectionHeader
          id="recent-heading"
          title={MESSAGES.dashboard.recent}
          action={
            view.entries.length > RECENT && (
              <Link to="/history" className="press flex min-h-10 items-center px-1 text-[15px] font-semibold text-primary">
                {MESSAGES.dashboard.seeAll}
              </Link>
            )
          }
        />
        {summary !== null && (
          <p className="flex gap-2 rounded-[16px] bg-surface-2 p-3 text-[15px]">
            <SparkIcon className="shrink-0 text-accent" width={20} height={20} />
            <span>{summary}</span>
          </p>
        )}
        {!view.loading && view.entries.length === 0 && (
          <p className="rounded-[16px] border border-dashed border-border p-5 text-center text-muted">{MESSAGES.dashboard.noEntries}</p>
        )}
        <JournalList entries={view.entries.slice(0, RECENT)} reversed={view.reversed} customers={byId} label={MESSAGES.ledger.title} />
      </section>
    </div>
  );
}
