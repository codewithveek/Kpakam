import type { CustomerId, Ulid } from '@/shared/ids/ulid';
import type { Kobo } from '@/shared/money/kobo';
import type { LagosDay } from '@/shared/time/lagos';

export const JOURNAL_SCHEMA_VERSION = 1;

export type JournalKind = 'sale' | 'expense' | 'repayment' | 'credit_sale' | 'reversal';

/** Ledger accounts (ledger.md). Receivables are per customer. */
export type Account = 'cash' | 'sales' | 'expenses' | `receivable:${string}`;

export interface JournalLine {
  readonly account: Account;
  readonly amountKobo: Kobo;
}

export type InferenceOrigin = 'on_device' | 'in_cloud' | 'manual';

export interface JournalSource {
  readonly inference: InferenceOrigin;
  readonly reason: string;
  readonly promptVersion: string | null;
}

/** One immutable double-entry journal. Lines sum to zero. */
export interface Journal {
  readonly id: Ulid;
  readonly idempotencyKey: string;
  readonly occurredAt: string;
  readonly day: LagosDay;
  readonly kind: JournalKind;
  readonly description: string;
  readonly customerId: CustomerId | null;
  readonly lines: readonly JournalLine[];
  readonly reverses: Ulid | null;
  readonly source: JournalSource;
  readonly schemaVersion: typeof JOURNAL_SCHEMA_VERSION;
}

/** The receivable account of one customer. */
export function receivableAccount(customerId: CustomerId): Account {
  return `receivable:${customerId}`;
}

/** Returns the customer id of a receivable account, or null for other accounts. */
export function customerOfAccount(account: Account): string | null {
  return account.startsWith('receivable:') ? account.slice('receivable:'.length) : null;
}

export type LedgerError = 'CUSTOMER_REQUIRED' | 'PAID_EXCEEDS_TOTAL' | 'AMOUNT_TOO_SMALL' | 'ALREADY_REVERSED';
