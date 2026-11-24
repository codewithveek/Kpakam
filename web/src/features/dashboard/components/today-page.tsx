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
  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={`${MESSAGES.greeting(new Date().getHours())},`} subtitle={formatLagosDate(today)} />
      <TodayTotals totals={totals} />
      <QuickEntryPanel />
      <JobList jobs={jobs} />
      <section aria-labelledby="recent-heading" className="flex flex-col gap-2">
        <SectionHeader
          id="recent-heading"
          title={MESSAGES.dashboard.recent}
          action={
            view.entries.length > RECENT && (
              <Link to="/history" className="press flex min-h-10 items-center rounded-full bg-surface px-4 text-[14px] font-semibold text-text">
                {MESSAGES.dashboard.seeAll}
              </Link>
            )
          }
        />
        {summary !== null && (
          <p className="flex gap-3 rounded-[24px] bg-surface p-4 text-[15px] leading-snug">
            <SparkIcon className="mt-0.5 shrink-0" width={18} height={18} />
            <span>{summary}</span>
          </p>
        )}
        {!view.loading && view.entries.length === 0 && (
          <p className="rounded-[24px] bg-surface p-6 text-center text-[15px] text-muted">{MESSAGES.dashboard.noEntries}</p>
        )}
        <JournalList entries={view.entries.slice(0, RECENT)} reversed={view.reversed} customers={byId} label={MESSAGES.ledger.title} />
      </section>
    </div>
  );
}
