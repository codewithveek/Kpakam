import type { HTMLAttributes, ReactNode } from 'react';

/** A list where every row is its own soft card, with small gaps (calm, scannable, thumb-friendly). */
export function ListGroup({ className = '', children, ...props }: HTMLAttributes<HTMLUListElement>) {
  return (
    <ul className={`flex flex-col gap-2 ${className}`} {...props}>
      {children}
    </ul>
  );
}

/** A grouped settings-style list: one card, rows separated by hairlines. */
export function ListPanel({ className = '', children, ...props }: HTMLAttributes<HTMLUListElement>) {
  return (
    <ul className={`divide-y divide-border overflow-hidden rounded-[24px] bg-surface ${className}`} {...props}>
      {children}
    </ul>
  );
}

export interface SectionHeaderProps {
  readonly id?: string;
  readonly title: string;
  readonly action?: ReactNode;
}

/** A section heading: small, quiet, with an optional trailing action. */
export function SectionHeader({ id, title, action }: SectionHeaderProps) {
  return (
    <div className="flex min-h-10 items-end justify-between px-1">
      <h2 id={id} className="text-[15px] font-semibold tracking-[-0.01em] text-muted">
        {title}
      </h2>
      {action}
    </div>
  );
}

export interface RowContentProps {
  readonly leading?: ReactNode;
  readonly title: ReactNode;
  readonly subtitle?: ReactNode;
  readonly trailing?: ReactNode;
}

/** The standard row layout: icon tile, title over subtitle, right-aligned value. */
export function RowContent({ leading, title, subtitle, trailing }: RowContentProps) {
  return (
    <span className="flex min-h-[72px] w-full items-center gap-3 px-4 py-3 text-left">
      {leading}
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[16px] font-semibold tracking-[-0.01em]">{title}</span>
        {subtitle !== undefined && <span className="flex min-w-0 items-center gap-1.5 truncate text-[13px] text-muted">{subtitle}</span>}
      </span>
      {trailing !== undefined && <span className="flex shrink-0 flex-col items-end gap-0.5">{trailing}</span>}
    </span>
  );
}
