import type { ReactNode } from 'react';

export interface PageHeaderProps {
  readonly title: string;
  readonly eyebrow?: string;
  readonly action?: ReactNode;
}

/** A large page title with tight tracking, plus an optional eyebrow and action. */
export function PageHeader({ title, eyebrow, action }: PageHeaderProps) {
  return (
    <header className="flex items-end justify-between gap-3 pt-2">
      <div>
        {eyebrow !== undefined && <p className="text-sm font-medium text-muted">{eyebrow}</p>}
        <h1 className="text-[28px] font-bold leading-tight tracking-[-0.02em]">{title}</h1>
      </div>
      {action}
    </header>
  );
}
