import type { Draft } from '@/inference/domain/draft';
import { newCustomerId, newUlid, type CustomerId, type Ulid } from '@/shared/ids/ulid';
import { asKobo, ZERO_KOBO } from '@/shared/money/kobo';
import { err, ok, type Result } from '@/shared/result';
import { customerNameKey, findCustomerByName, type Customer } from '../domain/customer';
import { buildJournal, buildReversal } from '../domain/build-journal';
import { checkLines } from '../domain/invariants';
import { customerOfAccount, type Journal, type JournalSource, type LedgerError } from '../domain/journal';
import type { CustomerChange, CustomerRepository, JournalRepository } from '../repository/journal-repository';

export type SaveError = LedgerError | 'INVARIANT_VIOLATION';

/** One draft the trader confirmed. `journalId` is fixed when the draft is shown, so a double tap is a no-op. */
export interface ConfirmedDraft {
  readonly draft: Draft;
  readonly journalId: Ulid;
  readonly idempotencyKey: string;
  readonly occurredAt: Date;
  readonly source: JournalSource;
}

export interface LedgerServiceDeps {
  readonly journals: JournalRepository;
  readonly customers: CustomerRepository;
  readonly clock?: () => Date;
}

interface CustomerResolution {
  readonly customerId: CustomerId | null;
  readonly create: Customer | null;
}

function resolveCustomer(name: string | null, known: Customer[]): CustomerResolution {
  if (name === null) return { customerId: null, create: null };
  const existing = findCustomerByName(known, name);
  if (existing !== null) return { customerId: existing.id, create: null };
  const created: Customer = { id: newCustomerId(), name: name.slice(0, 60), nameKey: customerNameKey(name).slice(0, 60), phone: null, balanceKobo: ZERO_KOBO };
  known.push(created);
  return { customerId: created.id, create: created };
}

/** Customer cache changes caused by journals: one create per new customer, one increment per balance change. */
export function customerChanges(journals: readonly Journal[], created: readonly Customer[]): CustomerChange[] {
  const deltas = new Map<string, number>();
  for (const { account, amountKobo } of journals.flatMap((j) => j.lines)) {
    const id = customerOfAccount(account);
    if (id !== null) deltas.set(id, (deltas.get(id) ?? 0) + amountKobo);
  }
  const createdIds = new Set<string>(created.map((c) => c.id));
  const creates: CustomerChange[] = created.map((customer) => ({
    type: 'create',
    customer: { ...customer, balanceKobo: asKobo(deltas.get(customer.id) ?? 0) },
  }));
  const increments: CustomerChange[] = [...deltas]
    .filter(([id, delta]) => !createdIds.has(id) && delta !== 0)
    .map(([id, delta]) => ({ type: 'increment', customerId: id as CustomerId, delta: asKobo(delta) }));
  return [...creates, ...increments];
}

interface Resolution {
  readonly known: Customer[];
  readonly created: Customer[];
}

function build(item: ConfirmedDraft, customers: Resolution): Result<Journal, SaveError> {
  const customer = resolveCustomer(item.draft.customerName, customers.known);
  if (customer.create !== null) customers.created.push(customer.create);
  const journal = buildJournal({
    draft: item.draft,
    customerId: customer.customerId,
    id: item.journalId,
    idempotencyKey: item.idempotencyKey,
    occurredAt: item.occurredAt,
    source: item.source,
  });
  if (!journal.ok) return journal;
  return checkLines(journal.value.lines).ok ? journal : err('INVARIANT_VIOLATION');
}

/**
 * Saves confirmed drafts as journals in one atomic batch, creating customers as needed.
 * Drafts whose journal already exists are skipped (idempotent).
 * @returns The journals written, or the first ledger error (nothing is written then).
 */
async function confirmDrafts(deps: LedgerServiceDeps, items: readonly ConfirmedDraft[]): Promise<Result<Journal[], SaveError>> {
  const customers: Resolution = { known: await deps.customers.list(), created: [] };
  const journals: Journal[] = [];
  for (const item of items) {
    if (await deps.journals.exists(item.journalId)) continue;
    const journal = build(item, customers);
    if (!journal.ok) return journal;
    journals.push(journal.value);
  }
  const used = new Set(journals.map((j) => j.customerId));
  const creates = customers.created.filter((c) => used.has(c.id));
  if (journals.length > 0) await deps.journals.commit({ journals, customers: customerChanges(journals, creates) });
  return ok(journals);
}

async function reversalOf(deps: LedgerServiceDeps, original: Journal): Promise<Result<Journal, SaveError>> {
  const alreadyReversed = await deps.journals.isReversed(original.id);
  return buildReversal({ original, alreadyReversed, id: newUlid(), occurredAt: (deps.clock ?? (() => new Date()))() });
}

/** Undo (F4): writes a reversal linked to the original. The original never changes. */
async function reverseEntry(deps: LedgerServiceDeps, original: Journal): Promise<Result<Journal, SaveError>> {
  const reversal = await reversalOf(deps, original);
  if (!reversal.ok) return reversal;
  await deps.journals.commit({ journals: [reversal.value], customers: customerChanges([reversal.value], []) });
  return reversal;
}

/** Fix (F4): a reversal plus a corrected entry, in one atomic batch, at the original time. */
async function fixEntry(deps: LedgerServiceDeps, change: { original: Journal; corrected: Draft }): Promise<Result<Journal, SaveError>> {
  const { original, corrected } = change;
  const reversal = await reversalOf(deps, original);
  if (!reversal.ok) return reversal;
  const customers: Resolution = { known: await deps.customers.list(), created: [] };
  const source: JournalSource = { inference: 'manual', reason: 'CORRECTION', promptVersion: null };
  const item = { draft: corrected, journalId: newUlid(), idempotencyKey: `fix:${original.id}`, occurredAt: new Date(Date.parse(original.occurredAt)), source };
  const replacement = build(item, customers);
  if (!replacement.ok) return replacement;
  const journals = [reversal.value, replacement.value];
  await deps.journals.commit({ journals, customers: customerChanges(journals, customers.created) });
  return replacement;
}

/** Ledger use cases: confirm drafts, undo, and fix. Never edits or deletes a journal. */
export function createLedgerService(deps: LedgerServiceDeps) {
  return {
    confirmDrafts: (items: readonly ConfirmedDraft[]) => confirmDrafts(deps, items),
    reverseEntry: (original: Journal) => reverseEntry(deps, original),
    fixEntry: (original: Journal, corrected: Draft) => fixEntry(deps, { original, corrected }),
  };
}

export type LedgerService = ReturnType<typeof createLedgerService>;
