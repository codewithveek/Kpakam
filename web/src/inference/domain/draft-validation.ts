import { nairaToKobo } from '@/shared/money/kobo';
import { err, ok, type Result } from '@/shared/result';
import { draftResponseSchema, type Draft, type DraftResponse } from './draft';

/** Human-readable problems, fed back to the model on retry. */
export type ValidationErrors = readonly string[];

function cleanText(value: string | null | undefined): string | null {
  if (value === null || value === undefined) return null;
  const trimmed = value.replace(/\s+/g, ' ').trim();
  return trimmed === '' ? null : trimmed;
}

/** Removes Markdown code fences that small models sometimes add. */
export function stripCodeFences(raw: string): string {
  const fenced = /^\s*```(?:json)?\s*([\s\S]*?)\s*```\s*$/i.exec(raw);
  return fenced?.[1] ?? raw.trim();
}

function parseJson(raw: string): Result<unknown, ValidationErrors> {
  try {
    return ok(JSON.parse(stripCodeFences(raw)) as unknown);
  } catch {
    return err(['Output is not valid JSON. Reply with one JSON object only.']);
  }
}

function normaliseDraft(draft: {
  kind: Draft['kind'];
  item?: string | null | undefined;
  quantity?: number | null | undefined;
  unit?: string | null | undefined;
  totalNaira: number;
  paidNaira?: number | null | undefined;
  customerName?: string | null | undefined;
  confidence: number;
}): Draft {
  return {
    kind: draft.kind,
    item: cleanText(draft.item),
    quantity: draft.quantity ?? null,
    unit: cleanText(draft.unit),
    totalNaira: draft.totalNaira,
    paidNaira: draft.kind === 'credit_sale' ? (draft.paidNaira ?? 0) : null,
    customerName: cleanText(draft.customerName),
    confidence: draft.confidence,
  };
}

/** Domain rules the JSON Schema cannot express (ledger.md). Returns problems for one draft. */
export function domainProblems(draft: Draft, index: number): string[] {
  const at = `drafts[${index}]`;
  const problems: string[] = [];
  const needsCustomer = draft.kind === 'repayment' || draft.kind === 'credit_sale';
  if (needsCustomer && draft.customerName === null) {
    problems.push(`${at}.customerName is required for kind "${draft.kind}".`);
  }
  if (nairaToKobo(draft.totalNaira) === 0) problems.push(`${at}.totalNaira must be at least 0.01.`);
  if (draft.kind === 'credit_sale' && (draft.paidNaira ?? 0) > draft.totalNaira) {
    problems.push(`${at}.paidNaira cannot be more than totalNaira.`);
  }
  return problems;
}

function schemaProblems(issues: readonly { path: readonly PropertyKey[]; message: string }[]): string[] {
  return issues.map((issue) => {
    const path = issue.path.map(String).join('.');
    return `${path === '' ? 'output' : path}: ${issue.message}`;
  });
}

/**
 * Parses and validates raw model output against the draft contract.
 * @param raw - The model's text output.
 * @returns The normalised response, or a list of problems to show the model on retry.
 */
export function validateDraftOutput(raw: string): Result<DraftResponse, ValidationErrors> {
  const parsed = parseJson(raw);
  if (!parsed.ok) return parsed;
  const checked = draftResponseSchema.safeParse(parsed.value);
  if (!checked.success) return err(schemaProblems(checked.error.issues));
  const drafts = checked.data.drafts.map(normaliseDraft);
  const needsClarification = cleanText(checked.data.needsClarification);
  const problems = drafts.flatMap(domainProblems);
  if (drafts.length === 0 && needsClarification === null) {
    problems.push('Return at least one draft, or ask a question in needsClarification.');
  }
  if (problems.length > 0) return err(problems);
  return ok({ drafts, needsClarification, transcript: cleanText(checked.data.transcript) });
}
