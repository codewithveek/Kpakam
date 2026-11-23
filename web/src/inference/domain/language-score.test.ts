import languageCases from '@contract/language-cases.json';
import { describe, expect, it } from 'vitest';
import { scoreLanguage } from './language-score';

describe('contract: language-cases.json', () => {
  it.each(languageCases.cases)('$text', ({ text, expectedScore }) => {
    expect(scoreLanguage(text)).toBe(expectedScore);
  });

  it('scores empty text as zero', () => {
    expect(scoreLanguage('')).toBe(0);
  });
});
