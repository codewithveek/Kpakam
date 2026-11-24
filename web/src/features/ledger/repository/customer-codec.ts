import * as z from 'zod/mini';
import { parseCustomerId } from '@/shared/ids/ulid';
import { asKobo } from '@/shared/money/kobo';
import { CUSTOMER_SCHEMA_VERSION, type Customer } from '../domain/customer';

const storedCustomer = z.object({
  id: z.string(),
  name: z.string(),
  nameKey: z.string(),
  phone: z.nullable(z.string()),
  balanceKobo: z.int(),
});

/** Converts stored data to a `Customer`, or null if it does not match the contract. */
export function decodeCustomer(data: unknown): Customer | null {
  const parsed = storedCustomer.safeParse(data);
  if (!parsed.success) return null;
  const id = parseCustomerId(parsed.data.id);
  return id === null ? null : { ...parsed.data, id, balanceKobo: asKobo(parsed.data.balanceKobo) };
}

/** Data for a new customer document (timestamps are added by the repository). */
export function encodeNewCustomer(customer: Customer): Record<string, unknown> {
  return {
    id: customer.id,
    name: customer.name,
    nameKey: customer.nameKey,
    phone: customer.phone,
    balanceKobo: customer.balanceKobo,
    schemaVersion: CUSTOMER_SCHEMA_VERSION,
  };
}
