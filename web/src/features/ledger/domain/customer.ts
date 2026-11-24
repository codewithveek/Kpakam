import type { CustomerId } from '@/shared/ids/ulid';
import type { Kobo } from '@/shared/money/kobo';

export const CUSTOMER_SCHEMA_VERSION = 1;

/** A customer in the debt book. `balanceKobo` is a rebuildable cache. */
export interface Customer {
  readonly id: CustomerId;
  readonly name: string;
  readonly nameKey: string;
  readonly phone: string | null;
  readonly balanceKobo: Kobo;
}

/** Normalises a name for matching: lower-case, trimmed, single spaces. */
export function customerNameKey(name: string): string {
  return name.normalize('NFC').toLowerCase().replace(/\s+/g, ' ').trim();
}

/** Finds an existing customer whose name matches, ignoring case and spacing. */
export function findCustomerByName(customers: readonly Customer[], name: string): Customer | null {
  const key = customerNameKey(name);
  return customers.find((customer) => customer.nameKey === key) ?? null;
}

/** Sorts the debt book: largest debts first, then by name. */
export function sortByDebt(customers: readonly Customer[]): Customer[] {
  return [...customers].sort((a, b) => b.balanceKobo - a.balanceKobo || a.nameKey.localeCompare(b.nameKey));
}
