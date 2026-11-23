declare const brand: unique symbol;

/** Nominal type: a `T` that only trusted constructors can create. */
export type Brand<T, Name extends string> = T & { readonly [brand]: Name };
