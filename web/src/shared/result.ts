/** A typed outcome for expected failures. Exceptions are for bugs only. */
export type Result<T, E> = { readonly ok: true; readonly value: T } | { readonly ok: false; readonly error: E };

/** Wraps a success value. */
export function ok<T>(value: T): { readonly ok: true; readonly value: T } {
  return { ok: true, value };
}

/** Wraps an expected failure. */
export function err<E>(error: E): { readonly ok: false; readonly error: E } {
  return { ok: false, error };
}

/** Fails the type check when a union is not handled exhaustively. */
export function assertNever(value: never): never {
  throw new Error(`Unhandled case: ${JSON.stringify(value)}`);
}
