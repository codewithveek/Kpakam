import { describe, expect, it } from 'vitest';
import { decodeSettings, DEFAULT_SETTINGS } from './settings';

describe('decodeSettings', () => {
  it('reads valid settings', () => {
    expect(decodeSettings({ dataSaver: true, voiceLanguage: 'pcm_yo', analyticsOptIn: true })).toEqual({
      dataSaver: true,
      voiceLanguage: 'pcm_yo',
      analyticsOptIn: true,
    });
  });

  it('falls back to defaults field by field for untrusted data', () => {
    expect(decodeSettings(undefined)).toEqual(DEFAULT_SETTINGS);
    expect(decodeSettings({ dataSaver: 'yes', voiceLanguage: 'fr', analyticsOptIn: 1 })).toEqual(DEFAULT_SETTINGS);
    expect(DEFAULT_SETTINGS.dataSaver).toBe(false);
    expect(DEFAULT_SETTINGS.analyticsOptIn).toBe(false);
  });
});
