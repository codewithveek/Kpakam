import type { CustomerId } from '@/shared/ids/ulid';
import type { Kobo } from '@/shared/money/kobo';
import type { LagosDay } from '@/shared/time/lagos';
import type { Customer } from '../domain/customer';
import type { Journal } from '../domain/journal';

/** An inclusive range of Lagos days. */
export interface DayRange {
  readonly from: LagosDay;
  readonly to: LagosDay;
}

/** A journal plus whether it still waits to reach the server (F2 "pending sync"). */
export interface JournalEntry {
  readonly journal: Journal;
  readonly pendingSync: boolean;
}

/** A new customer to create, or an existing one whose cached balance changes. */
export type CustomerChange =
  { readonly type: 'create'; readonly customer: Customer } | { readonly type: 'increment'; readonly customerId: CustomerId; readonly delta: Kobo };

/** One atomic write: journals plus the customer cache changes they cause. */
export interface LedgerCommit {
  readonly journals: readonly Journal[];
  readonly customers: readonly CustomerChange[];
}

/** Journal storage. Implemented over Firestore; tests use an in-memory fake. */
export interface JournalRepository {
  watchRange(range: DayRange, onChange: (entries: readonly JournalEntry[]) => void): () => void;
  watchCustomer(customerId: CustomerId, onChange: (entries: readonly JournalEntry[]) => void): () => void;
  exists(journalId: string): Promise<boolean>;
  isReversed(journalId: string): Promise<boolean>;
  /** Applies the batch to the local cache at once; the server write completes in the background. */
  commit(change: LedgerCommit): Promise<void>;
  listAll(): Promise<Journal[]>;
}

/** Customer storage (the debt book). */
export interface CustomerRepository {
  watchAll(onChange: (customers: readonly Customer[]) => void): () => void;
  /** Current customers from cache (works offline). */
  list(): Promise<Customer[]>;
}
