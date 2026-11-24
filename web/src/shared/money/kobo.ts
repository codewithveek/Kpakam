import type { Brand } from '../brand';

/** Integer kobo. 100 kobo = 1 naira. Signed: debits positive, credits negative. */
export type Kobo = Brand<number, 'Kobo'>;

/** The zero amount. */
export const ZERO_KOBO = 0 as Kobo;

/**
 * Converts a positive naira amount to integer kobo, rounding half up on the
 * decimal value (ledger.md). This is the only place naira becomes kobo.
 * Example: 1.005 → 101 kobo, where binary floating point would give 100.
 * @throws RangeError when the input is negative, not finite, or out of range.
 */
export function nairaToKobo(naira: number): Kobo {
  if (!Number.isFinite(naira) || naira < 0) throw new RangeError('Naira amount must be a finite, non-negative number');
  const text = String(naira);
  if (/e/i.test(text)) return asKobo(Math.floor(naira * 100 + 0.5));
  const [whole = '0', fraction = ''] = text.split('.');
  const digits = `${fraction}000`.slice(0, 3);
  const roundUp = Number(digits[2]) >= 5 ? 1 : 0;
  return asKobo(Number(whole) * 100 + Number(digits.slice(0, 2)) + roundUp);
}

/**
 * Narrows a stored integer to `Kobo`.
 * @throws RangeError when the value is not a safe integer.
 */
export function asKobo(value: number): Kobo {
  if (!Number.isSafeInteger(value)) throw new RangeError(`Not an integer kobo amount: ${value}`);
  return value as Kobo;
}

/** Adds kobo amounts exactly. */
export function addKobo(...amounts: readonly Kobo[]): Kobo {
  let total = 0;
  for (const amount of amounts) total += amount;
  return asKobo(total);
}

/** Subtracts `b` from `a`. */
export function subtractKobo(a: Kobo, b: Kobo): Kobo {
  return asKobo(a - b);
}

/** Flips the sign of an amount. */
export function negateKobo(amount: Kobo): Kobo {
  return asKobo(amount === 0 ? 0 : 0 - amount);
}

/** Sums a list of amounts. Returns zero for an empty list. */
export function sumKobo(amounts: Iterable<Kobo>): Kobo {
  let total = 0;
  for (const amount of amounts) total += amount;
  return asKobo(total);
}
