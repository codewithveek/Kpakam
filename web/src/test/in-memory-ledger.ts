import type { Customer } from '@/features/ledger/domain/customer';
import type { Journal } from '@/features/ledger/domain/journal';
import type { CustomerRepository, JournalEntry, JournalRepository, LedgerCommit } from '@/features/ledger/repository/journal-repository';
import { asKobo } from '@/shared/money/kobo';

/** In-memory repositories with the same contract as the Firestore ones, for tests. */
export function createInMemoryLedger() {
  const journals = new Map<string, Journal>();
  const customers = new Map<string, Customer>();
  const pending = new Set<string>();
  const watchers = new Set<() => void>();
  const commits: LedgerCommit[] = [];
  const notify = () => watchers.forEach((w) => w());
  const entries = (filter: (j: Journal) => boolean): JournalEntry[] =>
    [...journals.values()]
      .filter(filter)
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
      .map((journal) => ({ journal, pendingSync: pending.has(journal.id) }));

  function watch<T>(compute: () => T, onChange: (value: T) => void): () => void {
    const run = () => onChange(compute());
    watchers.add(run);
    run();
    return () => watchers.delete(run);
  }

  const journalRepo: JournalRepository = {
    watchRange: ({ from, to }, onChange) => watch(() => entries((j) => j.day >= from && j.day <= to), onChange),
    watchCustomer: (id, onChange) => watch(() => entries((j) => j.customerId === id), onChange),
    exists: (id) => Promise.resolve(journals.has(id)),
    isReversed: (id) => Promise.resolve([...journals.values()].some((j) => j.reverses === id)),
    commit(change) {
      commits.push(change);
      for (const c of change.customers) {
        if (c.type === 'create') customers.set(c.customer.id, c.customer);
        else {
          const current = customers.get(c.customerId);
          if (current === undefined) throw new Error('increment on missing customer');
          customers.set(c.customerId, { ...current, balanceKobo: asKobo(current.balanceKobo + c.delta) });
        }
      }
      for (const j of change.journals) {
        journals.set(j.id, j);
        pending.add(j.id);
      }
      notify();
      return Promise.resolve();
    },
    listAll: () => Promise.resolve([...journals.values()]),
  };

  const customerRepo: CustomerRepository = {
    watchAll: (onChange) => watch(() => [...customers.values()], onChange),
    list: () => Promise.resolve([...customers.values()]),
  };

  return {
    journalRepo,
    customerRepo,
    journals,
    customers,
    commits,
    sync: () => {
      pending.clear();
      notify();
    },
  };
}
