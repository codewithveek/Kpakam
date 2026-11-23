import type { Kobo } from './kobo';

const WHOLE = new Intl.NumberFormat('en-NG', {
  style: 'currency',
  currency: 'NGN',
  currencyDisplay: 'narrowSymbol',
  maximumFractionDigits: 0,
});
const EXACT = new Intl.NumberFormat('en-NG', {
  style: 'currency',
  currency: 'NGN',
  currencyDisplay: 'narrowSymbol',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Formats kobo as naira, for example ₦45,000 or ₦12.35 when there are kobo. */
export function formatNaira(amount: Kobo): string {
  const formatter = amount % 100 === 0 ? WHOLE : EXACT;
  return formatter.format(amount / 100);
}

/** Formats a naira number from a draft (not yet converted to kobo). */
export function formatDraftNaira(naira: number): string {
  return Number.isInteger(naira) ? WHOLE.format(naira) : EXACT.format(naira);
}
