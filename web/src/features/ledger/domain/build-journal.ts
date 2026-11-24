import type { Draft, DraftKind } from '@/inference/domain/draft';
import type { CustomerId, Ulid } from '@/shared/ids/ulid';
import { nairaToKobo, negateKobo, subtractKobo, type Kobo } from '@/shared/money/kobo';
import { err, ok, type Result } from '@/shared/result';
import { lagosDay, toLagosIso } from '@/shared/time/lagos';
import { JOURNAL_SCHEMA_VERSION, receivableAccount, type Journal, type JournalKind, type JournalLine, type JournalSource, type LedgerError } from './journal';

const MAX_DESCRIPTION = 120;
const KIND_LABEL: Readonly<Record<DraftKind, string>> = {
  sale: 'Sale',
  expense: 'Expense',
  repayment: 'Repayment',
  credit_sale: 'Credit sale',
};

export interface BuildJournalInput {
  readonly draft: Draft;
  readonly customerId: CustomerId | null;
  readonly id: Ulid;
  readonly idempotencyKey: string;
  readonly occurredAt: Date;
  readonly source: JournalSource;
}

interface Posting {
  readonly kind: JournalKind;
  readonly lines: readonly JournalLine[];
}

const line = (account: JournalLine['account'], amountKobo: Kobo): JournalLine => ({ account, amountKobo });

function formatQuantity(quantity: number | null): string | null {
  return quantity === null ? null : String(Number(quantity.toFixed(4)));
}

/** Builds the journal description (ledger.md "Description"). */
export function describeDraft(draft: Draft): string {
  const parts = [formatQuantity(draft.quantity), draft.unit, draft.item].filter((part): part is string => part !== null && part !== '');
  const text = parts.length > 0 ? parts.join(' ') : KIND_LABEL[draft.kind];
  return text.slice(0, MAX_DESCRIPTION);
}

function postCreditSale(amounts: { total: Kobo; paid: Kobo }, customerId: CustomerId | null): Result<Posting, LedgerError> {
  const { total, paid } = amounts;
  if (paid > total) return err('PAID_EXCEEDS_TOTAL');
  if (paid === total) return ok({ kind: 'sale', lines: [line('cash', total), line('sales', negateKobo(total))] });
  if (customerId === null) return err('CUSTOMER_REQUIRED');
  const receivable = line(receivableAccount(customerId), subtractKobo(total, paid));
  const lines = paid === 0 ? [receivable] : [line('cash', paid), receivable];
  return ok({ kind: 'credit_sale', lines: [...lines, line('sales', negateKobo(total))] });
}

function post(draft: Draft, customerId: CustomerId | null): Result<Posting, LedgerError> {
  const total = nairaToKobo(draft.totalNaira);
  if (total === 0) return err('AMOUNT_TOO_SMALL');
  switch (draft.kind) {
    case 'sale':
      return ok({ kind: 'sale', lines: [line('cash', total), line('sales', negateKobo(total))] });
    case 'expense':
      return ok({ kind: 'expense', lines: [line('expenses', total), line('cash', negateKobo(total))] });
    case 'repayment':
      if (customerId === null) return err('CUSTOMER_REQUIRED');
      return ok({ kind: 'repayment', lines: [line('cash', total), line(receivableAccount(customerId), negateKobo(total))] });
    case 'credit_sale':
      return postCreditSale({ total, paid: nairaToKobo(draft.paidNaira ?? 0) }, customerId);
  }
}

/**
 * Turns one validated draft into an immutable journal (ledger.md "Draft to journal").
 * The model's numbers are input, never truth: this function enforces the rules.
 * @returns The journal, or `CUSTOMER_REQUIRED` | `PAID_EXCEEDS_TOTAL` | `AMOUNT_TOO_SMALL`.
 */
export function buildJournal(input: BuildJournalInput): Result<Journal, LedgerError> {
  const posting = post(input.draft, input.customerId);
  if (!posting.ok) return posting;
  return ok({
    id: input.id,
    idempotencyKey: input.idempotencyKey,
    occurredAt: toLagosIso(input.occurredAt),
    day: lagosDay(input.occurredAt),
    kind: posting.value.kind,
    description: describeDraft(input.draft),
    customerId: input.customerId,
    lines: posting.value.lines,
    reverses: null,
    source: input.source,
    schemaVersion: JOURNAL_SCHEMA_VERSION,
  });
}

export interface BuildReversalInput {
  readonly original: Journal;
  readonly alreadyReversed: boolean;
  readonly id: Ulid;
  readonly occurredAt: Date;
}

/**
 * Builds the reversal of a journal: same lines, signs flipped (ledger.md "Reversal").
 * @returns The reversal journal, or `ALREADY_REVERSED`.
 */
export function buildReversal(input: BuildReversalInput): Result<Journal, LedgerError> {
  if (input.alreadyReversed) return err('ALREADY_REVERSED');
  const { original } = input;
  return ok({
    id: input.id,
    idempotencyKey: `reverse:${original.id}`,
    occurredAt: toLagosIso(input.occurredAt),
    day: lagosDay(input.occurredAt),
    kind: 'reversal',
    description: `Reversal: ${original.description}`.slice(0, MAX_DESCRIPTION),
    customerId: original.customerId,
    lines: original.lines.map((l) => line(l.account, negateKobo(l.amountKobo))),
    reverses: original.id,
    source: { inference: 'manual', reason: 'REVERSAL', promptVersion: null },
    schemaVersion: JOURNAL_SCHEMA_VERSION,
  });
}
