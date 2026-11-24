import { describe, expect, it } from 'vitest';
import { toInferenceError } from './firebase-inference-client';

describe('toInferenceError', () => {
  it.each([
    [{ code: 'api-not-enabled', message: 'x' }, 'DeviceModelUnavailable'],
    [new Error('Local LanguageModel API not available'), 'DeviceModelUnavailable'],
    [{ code: 'fetch-error', message: 'x' }, 'Offline'],
    [new Error('Failed to fetch'), 'Offline'],
    [new Error('[429] RESOURCE_EXHAUSTED'), 'QuotaExceeded'],
    [new Error('Response was blocked due to SAFETY'), 'Blocked'],
    [new Error('boom'), 'CloudFailed'],
  ])('maps %o to %s', (error, type) => {
    expect(toInferenceError(error).type).toBe(type);
  });
});
