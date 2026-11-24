import type { HTMLAttributes } from 'react';

export interface CardProps extends HTMLAttributes<HTMLElement> {
  /** `ink` inverts the card (black in light mode, white in dark mode) for the one most important block. */
  readonly tone?: 'surface' | 'ink' | 'soft';
}

const TONES = { surface: 'bg-surface text-text', ink: 'bg-ink text-on-ink', soft: 'bg-surface-2 text-text' } as const;

/** A large rounded surface (28 px). No borders or gradients; hierarchy comes from tone and space. */
export function Card({ tone = 'surface', className = '', ...props }: CardProps) {
  return <section className={`rounded-[28px] p-5 ${TONES[tone]} ${className}`} {...props} />;
}
