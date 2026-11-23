import * as z from 'zod/mini';
import { en } from 'zod/locales';

// English issue messages, so retry prompts tell the model exactly what was wrong.
z.config(en());

/** Draft kinds a model may return (packages/contract/schemas/draft.schema.json). */
export const DRAFT_KINDS = ['sale', 'credit_sale', 'repayment', 'expense'] as const;
export type DraftKind = (typeof DRAFT_KINDS)[number];

const MAX_NAIRA = 100_000_000;

/** Zod mirror of draft.schema.json `$defs.draft`. Parity is tested against the JSON Schema. */
export const draftSchema = z.strictObject({
  kind: z.enum(DRAFT_KINDS),
  item: z.nullish(z.string().check(z.maxLength(80))),
  quantity: z.nullish(z.number().check(z.positive())),
  unit: z.nullish(z.string().check(z.maxLength(30))),
  totalNaira: z.number().check(z.positive(), z.lte(MAX_NAIRA)),
  paidNaira: z.nullish(z.number().check(z.gte(0), z.lte(MAX_NAIRA))),
  customerName: z.nullish(z.string().check(z.maxLength(60))),
  confidence: z.number().check(z.gte(0), z.lte(1)),
});

/** Zod mirror of draft.schema.json (the whole response). */
export const draftResponseSchema = z.strictObject({
  drafts: z.array(draftSchema).check(z.maxLength(5)),
  needsClarification: z.nullish(z.string().check(z.maxLength(200))),
  transcript: z.nullish(z.string().check(z.maxLength(1000))),
});

/** A validated, normalised draft. Absent optional fields are `null`. */
export interface Draft {
  readonly kind: DraftKind;
  readonly item: string | null;
  readonly quantity: number | null;
  readonly unit: string | null;
  readonly totalNaira: number;
  readonly paidNaira: number | null;
  readonly customerName: string | null;
  readonly confidence: number;
}

/** A validated model response. */
export interface DraftResponse {
  readonly drafts: readonly Draft[];
  readonly needsClarification: string | null;
  readonly transcript: string | null;
}
