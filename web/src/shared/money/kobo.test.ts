import { describe, expect, it } from 'vitest';
import { formatDraftNaira, formatNaira } from './format';
import { addKobo, asKobo, nairaToKobo, negateKobo, subtractKobo, sumKobo } from './kobo';

describe('nairaToKobo', () => {
  it.each([
    [45000, 4_500_000],
    [12.345, 1235],
    [1.005, 101],
    [2.675, 268],
    [0.004, 0],
    [0.005, 1],
    [19.995, 2000],
    [1e-7, 0],
  ])('%s naira → %s kobo', (naira, kobo) => {
    expect(nairaToKobo(naira)).toBe(kobo);
  });

  it('rejects negative and non-finite input', () => {
    expect(() => nairaToKobo(-1)).toThrow(RangeError);
    expect(() => nairaToKobo(Number.NaN)).toThrow(RangeError);
  });
});

describe('kobo arithmetic', () => {
  it('adds, subtracts, negates, sums exactly', () => {
    expect(addKobo(asKobo(10), asKobo(-3))).toBe(7);
    expect(subtractKobo(asKobo(10), asKobo(3))).toBe(7);
    expect(negateKobo(asKobo(0))).toBe(0);
    expect(Object.is(negateKobo(asKobo(0)), -0)).toBe(false);
    expect(sumKobo([asKobo(1), asKobo(2)])).toBe(3);
    expect(() => asKobo(1.5)).toThrow(RangeError);
  });
});

describe('formatNaira', () => {
  it('formats whole naira without decimals and kobo with two', () => {
    expect(formatNaira(asKobo(4_500_000))).toBe('₦45,000');
    expect(formatNaira(asKobo(1235))).toBe('₦12.35');
    expect(formatDraftNaira(45000)).toBe('₦45,000');
    expect(formatDraftNaira(1.5)).toBe('₦1.50');
  });
});
