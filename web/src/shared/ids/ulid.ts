import { monotonicFactory } from 'ulid';
import type { Brand } from '../brand';

/** A 26-character Crockford base32 ULID. */
export type Ulid = Brand<string, 'Ulid'>;

/** A customer document id (a ULID). */
export type CustomerId = Brand<string, 'CustomerId'>;

const ULID_PATTERN = /^[0-9A-HJKMNP-TV-Z]{26}$/;
const nextUlid = monotonicFactory();

/** Creates a new, sortable ULID. Works offline without collisions. */
export function newUlid(now: number = Date.now()): Ulid {
  return nextUlid(now) as Ulid;
}

/** Returns true when `value` is a well-formed ULID. */
export function isUlid(value: string): value is Ulid {
  return ULID_PATTERN.test(value);
}

/** Narrows a stored string to a `Ulid`, or returns null. */
export function parseUlid(value: string): Ulid | null {
  return isUlid(value) ? value : null;
}

/** Creates a new customer id. */
export function newCustomerId(): CustomerId {
  return newUlid() as string as CustomerId;
}

/** Narrows a stored string to a `CustomerId`, or returns null. */
export function parseCustomerId(value: string): CustomerId | null {
  return ULID_PATTERN.test(value) ? (value as CustomerId) : null;
}
