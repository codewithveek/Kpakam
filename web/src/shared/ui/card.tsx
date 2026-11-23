import type { HTMLAttributes } from 'react';

/** A bordered surface. */
export function Card({ className = '', ...props }: HTMLAttributes<HTMLElement>) {
  return <section className={`rounded-2xl border border-border bg-surface p-4 ${className}`} {...props} />;
}
