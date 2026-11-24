import { describe, expect, it } from 'vitest';
import { stripCodeFences, validateDraftOutput } from './draft-validation';

const good = {
  drafts: [{ kind: 'credit_sale', item: ' rice ', quantity: 3, unit: 'bag', totalNaira: 45000, paidNaira: 35000, customerName: 'Mama  Bisi', confidence: 0.9 }],
  needsClarification: null,
};

describe('validateDraftOutput', () => {
  it('accepts and normalises a valid response', () => {
    const result = validateDraftOutput(JSON.stringify(good));
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.drafts[0]).toMatchObject({ item: 'rice', customerName: 'Mama Bisi', paidNaira: 35000 });
    expect(result.value.transcript).toBeNull();
  });

  it('accepts fenced JSON from small models', () => {
    expect(validateDraftOutput(`\`\`\`json\n${JSON.stringify(good)}\n\`\`\``).ok).toBe(true);
    expect(stripCodeFences('  {"a":1} ')).toBe('{"a":1}');
  });

  it('defaults missing paidNaira to 0 for credit sales and drops it for others', () => {
    const result = validateDraftOutput(
      JSON.stringify({
        drafts: [
          { kind: 'credit_sale', totalNaira: 10, customerName: 'A', confidence: 1 },
          { kind: 'sale', totalNaira: 10, paidNaira: 5, confidence: 1 },
        ],
      }),
    );
    expect(result.ok && result.value.drafts.map((d) => d.paidNaira)).toEqual([0, null]);
  });

  it.each([
    ['not JSON', 'sold rice', /not valid JSON/],
    ['schema violation', JSON.stringify({ drafts: [{ kind: 'gift', totalNaira: 1, confidence: 1 }] }), /kind/],
    ['extra field', JSON.stringify({ drafts: [], needsClarification: 'x', html: '<b>' }), /html|Unrecognized/i],
    ['too many drafts', JSON.stringify({ drafts: Array.from({ length: 6 }, () => ({ kind: 'sale', totalNaira: 1, confidence: 1 })) }), /drafts/],
    ['missing customer', JSON.stringify({ drafts: [{ kind: 'repayment', totalNaira: 5, confidence: 1 }] }), /customerName is required/],
    [
      'paid over total',
      JSON.stringify({ drafts: [{ kind: 'credit_sale', totalNaira: 5, paidNaira: 9, customerName: 'A', confidence: 1 }] }),
      /paidNaira cannot/,
    ],
    ['sub-kobo total', JSON.stringify({ drafts: [{ kind: 'sale', totalNaira: 0.001, confidence: 1 }] }), /at least 0.01/],
    ['nothing usable', JSON.stringify({ drafts: [], needsClarification: '  ' }), /at least one draft/],
  ])('rejects %s', (_name, raw, pattern) => {
    const result = validateDraftOutput(raw);
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.error.join('\n')).toMatch(pattern);
  });

  it('accepts a clarification question with no drafts', () => {
    const result = validateDraftOutput(JSON.stringify({ drafts: [], needsClarification: 'How much was it?' }));
    expect(result.ok && result.value.needsClarification).toBe('How much was it?');
  });
});
