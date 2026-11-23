import { useMemo } from 'react';
import { computeDayTotals, headlineAmount, useCustomers, useJournalRange, type Journal } from '@/features/ledger';
import { addDays, lagosDay, weekStart } from '@/shared/time/lagos';

const TOP = 5;

function bestSellers(journals: readonly Journal[]): { item: string; naira: number }[] {
  const byItem = new Map<string, number>();
  for (const j of journals) {
    if (j.kind === 'sale' || j.kind === 'credit_sale') byItem.set(j.description, (byItem.get(j.description) ?? 0) + headlineAmount(j));
  }
  return [...byItem]
    .sort((a, b) => b[1] - a[1])
    .slice(0, TOP)
    .map(([item, kobo]) => ({ item, naira: kobo / 100 }));
}

/** Aggregates for the weekly report: totals, best sellers, top debtors. No raw notes. */
export function useWeekData() {
  const today = lagosDay(new Date());
  const from = weekStart(today);
  const thisWeek = useJournalRange(from, today);
  const lastWeek = useJournalRange(addDays(from, -7), addDays(from, -1));
  const { customers } = useCustomers();
  return useMemo(() => {
    const journals = thisWeek.entries.map((e) => e.journal);
    const toNaira = (t: ReturnType<typeof computeDayTotals>) => ({
      sales: t.sales / 100,
      expenses: t.expenses / 100,
      profit: t.profit / 100,
      debtCollected: t.debtCollected / 100,
    });
    return {
      week: { from, to: today, ...toNaira(computeDayTotals(journals)) },
      lastWeek: toNaira(computeDayTotals(lastWeek.entries.map((e) => e.journal))),
      bestSellers: bestSellers(journals.filter((j) => !thisWeek.reversed.has(j.id))),
      owedBy: customers
        .filter((c) => c.balanceKobo > 0)
        .slice(0, TOP)
        .map((c) => ({ name: c.name, naira: c.balanceKobo / 100 })),
    };
  }, [thisWeek, lastWeek, customers, from, today]);
}
