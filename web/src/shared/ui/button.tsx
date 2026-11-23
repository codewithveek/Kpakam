import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

const VARIANTS: Readonly<Record<Variant, string>> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-strong disabled:opacity-50',
  secondary: 'bg-surface-2 text-text border border-border hover:border-primary disabled:opacity-50',
  ghost: 'bg-transparent text-text hover:bg-surface-2 disabled:opacity-50',
  danger: 'bg-danger-soft text-danger border border-danger/30 hover:border-danger disabled:opacity-50',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: Variant;
  readonly icon?: ReactNode;
}

/** Button with a 48 px minimum touch target (WCAG 2.2 AA, PRD accessibility). */
export function Button({ variant = 'primary', icon, className = '', children, type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-12 min-w-12 items-center justify-center gap-2 rounded-xl px-4 font-semibold transition-colors disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      {icon}
      {children}
    </button>
  );
}
