import { describe, expect, it } from 'vitest';
import { addDays, lagosDay, toLagosIso, weekStart } from './lagos';

describe('Lagos time', () => {
  it('formats ISO with the +01:00 offset', () => {
    expect(toLagosIso(new Date('2026-11-24T08:41:05.123Z'))).toBe('2026-11-24T09:41:05+01:00');
  });
  it('rolls the day at Lagos midnight', () => {
    expect(lagosDay(new Date('2026-11-24T22:59:00Z'))).toBe('2026-11-24');
    expect(lagosDay(new Date('2026-11-24T23:00:00Z'))).toBe('2026-11-25');
  });
  it('does day arithmetic and finds Monday', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(weekStart('2026-11-26')).toBe('2026-11-23');
    expect(weekStart('2026-11-23')).toBe('2026-11-23');
    expect(weekStart('2026-11-29')).toBe('2026-11-23');
  });
});
