import type { ReactNode } from 'react';

export interface PageHeaderProps {
  readonly title: string;
  readonly eyebrow?: string;
  /** A second headline line in grey (two-tone title). */
  readonly subtitle?: string;
  readonly action?: ReactNode;
  readonly leading?: ReactNode;
}

/** A large page title with tight tracking. Round icon buttons sit on the right, like a top bar. */
export function PageHeader({ title, eyebrow, subtitle, action, leading }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-4 pt-2">
      {(leading !== undefined || action !== undefined) && (
        <div className="flex items-center justify-between gap-2">
          <div>{leading}</div>
          <div className="flex items-center gap-2">{action}</div>
        </div>
      )}
      <div>
        {eyebrow !== undefined && <p className="text-[15px] font-medium text-muted">{eyebrow}</p>}
        <h1 className="display text-[34px] font-bold">
          {title}
          {subtitle !== undefined && <span className="block text-muted">{subtitle}</span>}
        </h1>
      </div>
    </header>
  );
}
