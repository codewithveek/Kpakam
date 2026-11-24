import { describe, expect, it } from 'vitest';
import { loudness } from './soundwave';

describe('loudness', () => {
  it('is zero for silence and grows with amplitude, capped at 1', () => {
    expect(loudness(new Uint8Array(64).fill(128))).toBe(0);
    const quiet = loudness(Uint8Array.from({ length: 64 }, (_, i) => (i % 2 === 0 ? 133 : 123)));
    const loud = loudness(Uint8Array.from({ length: 64 }, (_, i) => (i % 2 === 0 ? 200 : 56)));
    expect(quiet).toBeGreaterThan(0);
    expect(loud).toBeGreaterThan(quiet);
    expect(loudness(Uint8Array.from({ length: 64 }, (_, i) => (i % 2 === 0 ? 255 : 0)))).toBe(1);
    expect(loudness(new Uint8Array(0))).toBe(0);
  });
});
