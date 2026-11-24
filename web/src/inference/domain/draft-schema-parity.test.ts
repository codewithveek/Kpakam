import enGolden from '@contract/golden/en.jsonl?raw';
import pcmGolden from '@contract/golden/pcm.jsonl?raw';
import draftJsonSchema from '@contract/schemas/draft.schema.json';
import Ajv2020 from 'ajv/dist/2020.js';
import { describe, expect, it } from 'vitest';
import { draftResponseSchema } from './draft';

const validateJson = new Ajv2020({ allErrors: true, allowUnionTypes: true, validateFormats: false }).compile(draftJsonSchema);
const golden = [...enGolden.trim().split('\n'), ...pcmGolden.trim().split('\n')].map(
  (line) => JSON.parse(line) as { id: string; expected: Record<string, unknown> },
);

/** Mutations that make a response invalid in exactly one way. */
const MUTATIONS: readonly [string, (r: Record<string, unknown>) => unknown][] = [
  ['unknown kind', (r) => ({ ...r, drafts: [{ kind: 'gift', totalNaira: 1, confidence: 1 }] })],
  ['zero total', (r) => ({ ...r, drafts: [{ kind: 'sale', totalNaira: 0, confidence: 1 }] })],
  ['negative paid', (r) => ({ ...r, drafts: [{ kind: 'credit_sale', totalNaira: 5, paidNaira: -1, confidence: 1 }] })],
  ['confidence above 1', (r) => ({ ...r, drafts: [{ kind: 'sale', totalNaira: 5, confidence: 1.5 }] })],
  ['extra draft field', (r) => ({ ...r, drafts: [{ kind: 'sale', totalNaira: 5, confidence: 1, note: 'x' }] })],
  ['extra top-level field', (r) => ({ ...r, html: '<b>' })],
  ['six drafts', (r) => ({ ...r, drafts: Array.from({ length: 6 }, () => ({ kind: 'sale', totalNaira: 1, confidence: 1 })) })],
  ['missing drafts', ({ drafts: _drafts, ...rest }) => rest],
  ['string amount', (r) => ({ ...r, drafts: [{ kind: 'sale', totalNaira: '5000', confidence: 1 }] })],
  ['long customer name', (r) => ({ ...r, drafts: [{ kind: 'sale', totalNaira: 5, confidence: 1, customerName: 'x'.repeat(61) }] })],
];

describe('contract parity: Zod mirror vs draft.schema.json', () => {
  it.each(golden)('$id expected output is valid in both', ({ expected }) => {
    expect(validateJson(expected)).toBe(true);
    expect(draftResponseSchema.safeParse(expected).success).toBe(true);
  });

  it.each(MUTATIONS)('%s is rejected by both', (_name, mutate) => {
    const sample = mutate({ drafts: [], needsClarification: 'q' });
    expect(validateJson(sample)).toBe(false);
    expect(draftResponseSchema.safeParse(sample).success).toBe(false);
  });
});
