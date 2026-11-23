import type { HTMLAttributes, ReactNode } from 'react';

/** An inset grouped list: one raised surface, rows separated by hairlines. */
export function ListGroup({ className = '', children, ...props }: HTMLAttributes<HTMLUListElement>) {
  return (
    <ul className={`divide-y divide-border overflow-hidden rounded-[20px] border border-border bg-surface shadow-card ${className}`} {...props}>
      {children}
    </ul>
  );
}

export interface SectionHeaderProps {
  readonly id?: string;
  readonly title: string;
  readonly action?: ReactNode;
}

/** A small section heading with an optional trailing action. */
export function SectionHeader({ id, title, action }: SectionHeaderProps) {
  return (
    <div className="flex min-h-10 items-end justify-between px-1">
      <h2 id={id} className="text-[13px] font-semibold uppercase tracking-[0.06em] text-muted">
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

/** The standard row layout: avatar, title over subtitle, right-aligned value. */
export function RowContent({ leading, title, subtitle, trailing }: RowContentProps) {
  return (
    <span className="flex min-h-16 w-full items-center gap-3 px-4 py-3 text-left">
      {leading}
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[15px] font-semibold">{title}</span>
        {subtitle !== undefined && <span className="flex min-w-0 items-center gap-1.5 truncate text-[13px] text-muted">{subtitle}</span>}
      </span>
      {trailing !== undefined && <span className="flex shrink-0 flex-col items-end">{trailing}</span>}
    </span>
  );
}
