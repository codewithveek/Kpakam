import { useEffect, useRef, useState } from 'react';
import { useServices } from '@/app/services';
import type { DayTotals } from '@/features/ledger';

const DEBOUNCE_MS = 1500;

/** A short on-device summary of today's totals. Null when the model cannot run; numbers still show. */
export function useDaySummary(totals: DayTotals): string | null {
  const { textTasks } = useServices();
  const [summary, setSummary] = useState<string | null>(null);
  const lastKey = useRef('');
  useEffect(() => {
    const key = `${totals.sales}:${totals.expenses}:${totals.debtCollected}:${totals.entries}`;
    if (totals.entries === 0 || key === lastKey.current) return undefined;
    const timer = setTimeout(() => {
      lastKey.current = key;
      const naira = {
        sales: totals.sales / 100,
        expenses: totals.expenses / 100,
        profit: totals.profit / 100,
        debtCollected: totals.debtCollected / 100,
        entries: totals.entries,
      };
      void textTasks.summariseDay(naira).then(setSummary);
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [textTasks, totals]);
  return totals.entries === 0 ? null : summary;
}
