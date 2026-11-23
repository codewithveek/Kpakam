import * as z from 'zod/mini';
import { parseCustomerId, parseUlid, type Ulid } from '@/shared/ids/ulid';
import { asKobo } from '@/shared/money/kobo';
import { JOURNAL_SCHEMA_VERSION, type Account, type Journal } from '../domain/journal';

const ULID = /^[0-9A-HJKMNP-TV-Z]{26}$/;

const ulid = z.string().check(z.regex(ULID));
const storedJournal = z.object({
  id: ulid,
  idempotencyKey: z.string(),
  occurredAt: z.string(),
  day: z.string(),
  kind: z.enum(['sale', 'expense', 'repayment', 'credit_sale', 'reversal']),
  description: z.string(),
  customerId: z.nullable(ulid),
  lines: z.array(z.object({ account: z.string().check(z.regex(/^(cash|sales|expenses|receivable:[0-9A-HJKMNP-TV-Z]{26})$/)), amountKobo: z.int() })),
  reverses: z.nullable(ulid),
  source: z.object({ inference: z.enum(['on_device', 'in_cloud', 'manual']), reason: z.string(), promptVersion: z.nullable(z.string()) }),
  schemaVersion: z.literal(JOURNAL_SCHEMA_VERSION),
});

/**
 * Converts stored Firestore data to a `Journal`. Returns null for documents that
 * do not match the contract (they are skipped, never "repaired").
 */
export function decodeJournal(data: unknown): Journal | null {
  const parsed = storedJournal.safeParse(data);
  if (!parsed.success) return null;
  const d = parsed.data;
  return {
    ...d,
    id: d.id as Ulid,
    customerId: d.customerId === null ? null : parseCustomerId(d.customerId),
    reverses: d.reverses === null ? null : parseUlid(d.reverses),
    lines: d.lines.map((line) => ({ account: line.account as Account, amountKobo: asKobo(line.amountKobo) })),
    schemaVersion: JOURNAL_SCHEMA_VERSION,
  };
}

/** Converts a `Journal` to plain Firestore data (without `createdAt`, which is a server timestamp). */
export function encodeJournal(journal: Journal): Record<string, unknown> {
  return {
    id: journal.id,
    idempotencyKey: journal.idempotencyKey,
    occurredAt: journal.occurredAt,
    day: journal.day,
    kind: journal.kind,
    description: journal.description,
    customerId: journal.customerId,
    lines: journal.lines.map((line) => ({ account: line.account, amountKobo: line.amountKobo })),
    reverses: journal.reverses,
    source: { ...journal.source },
    schemaVersion: journal.schemaVersion,
  };
}
