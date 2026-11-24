import { useEffect, useMemo, useState } from 'react';
import { useServices } from '@/app/services';
import type { CustomerId } from '@/shared/ids/ulid';
import type { LagosDay } from '@/shared/time/lagos';
import { reversedIds } from '../domain/totals';
import type { JournalEntry } from '../repository/journal-repository';

export interface JournalView {
  readonly entries: readonly JournalEntry[];
  readonly loading: boolean;
  readonly reversed: ReadonlySet<string>;
  readonly pendingCount: number;
}

function useEntries(subscribe: (onChange: (entries: readonly JournalEntry[]) => void) => () => void, key: string): JournalView {
  const [state, setState] = useState<{ key: string; entries: readonly JournalEntry[] } | null>(null);
  useEffect(() => subscribe((entries) => setState({ key, entries })), [subscribe, key]);
  return useMemo(() => {
    const entries = state?.key === key ? state.entries : [];
    return {
      entries,
      loading: state?.key !== key,
      reversed: reversedIds(entries.map((e) => e.journal)),
      pendingCount: entries.filter((e) => e.pendingSync).length,
    };
  }, [state, key]);
}

/** Live journals for a Lagos day range. Listener detaches on unmount. */
export function useJournalRange(from: LagosDay, to: LagosDay): JournalView {
  const { journals } = useServices();
  const subscribe = useMemo(() => (onChange: (e: readonly JournalEntry[]) => void) => journals.watchRange({ from, to }, onChange), [journals, from, to]);
  return useEntries(subscribe, `${from}:${to}`);
}

/** Live journals of one customer. */
export function useCustomerJournals(customerId: CustomerId): JournalView {
  const { journals } = useServices();
  const subscribe = useMemo(() => (onChange: (e: readonly JournalEntry[]) => void) => journals.watchCustomer(customerId, onChange), [journals, customerId]);
  return useEntries(subscribe, customerId);
}
