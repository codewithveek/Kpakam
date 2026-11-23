import { describe, expect, it } from 'vitest';
import { modelDraftSchema } from './model-schema';

describe('modelDraftSchema', () => {
  it('inlines $defs and strips meta keys', () => {
    const schema = modelDraftSchema();
    const text = JSON.stringify(schema);
    expect(text).not.toContain('$ref');
    expect(text).not.toContain('$schema');
    expect(text).toContain('credit_sale');
    expect(schema['required']).toEqual(['drafts']);
  });
});
