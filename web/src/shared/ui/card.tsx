import type { HTMLAttributes } from 'react';

/** A raised surface: hairline border (stays visible in sunlight) plus a soft shadow. */
export function Card({ className = '', ...props }: HTMLAttributes<HTMLElement>) {
  return <section className={`rounded-[20px] border border-border bg-surface p-4 shadow-card ${className}`} {...props} />;
}
