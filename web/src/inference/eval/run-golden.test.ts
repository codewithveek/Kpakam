import enGolden from '@contract/golden/en.jsonl?raw';
import { describe, expect, it } from 'vitest';
import { createScriptedInferenceClient } from '../clients/scripted-inference-client';
import { draftResponseSchema } from '../domain/draft';
import { PROMPTS } from '../prompt-library';
import { parseGolden, runGolden } from './run-golden';

const validate = (data: unknown) => draftResponseSchema.safeParse(data).success;

describe('runGolden', () => {
  it('scores a perfect model at 100% and a broken one at 0%', async () => {
    const lines = parseGolden(enGolden).slice(0, 5);
    const perfect = createScriptedInferenceClient({ device: lines.map((l) => ({ text: JSON.stringify(l.expected) })) });
    const good = await runGolden(lines, { client: perfect.client, target: 'device', prompt: PROMPTS.parse, validate });
    expect(good).toMatchObject({ total: 5, validPct: 100, correctPct: 100, promptVersion: 'parse-v1' });
    expect(good.fieldPct.kind).toBe(100);

    const broken = createScriptedInferenceClient({ fallback: () => ({ text: 'not json' }) });
    const bad = await runGolden(lines, { client: broken.client, target: 'device', prompt: PROMPTS.parse, validate });
    expect(bad).toMatchObject({ validPct: 0, correctPct: 0 });
    expect(bad.failures[0]?.reason).toBe('invalid output');
  });
});
