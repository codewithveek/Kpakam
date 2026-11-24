import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'soft';
type Size = 'md' | 'lg' | 'icon';

const VARIANTS: Readonly<Record<Variant, string>> = {
  // Black pill: the one strong action on a screen.
  primary: 'bg-primary text-on-primary hover-fine:bg-primary-strong disabled:bg-surface-3 disabled:text-muted disabled:opacity-100',
  // White pill on grey canvas, or grey pill inside a white card.
  secondary: 'bg-surface text-text border border-border',
  soft: 'bg-surface-2 text-text',
  ghost: 'bg-transparent text-text hover-fine:bg-surface-2',
  danger: 'bg-danger-soft text-danger',
};

const SIZES: Readonly<Record<Size, string>> = {
  md: 'min-h-12 px-5 text-[15px]',
  lg: 'min-h-14 px-6 text-[16px]',
  icon: 'h-12 w-12',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: Variant;
  readonly size?: Size;
  readonly icon?: ReactNode;
}

/** Pill button with a 48 px minimum touch target and press feedback (scale 0.97). */
export function Button({ variant = 'primary', size = 'md', icon, className = '', children, type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`press inline-flex shrink-0 items-center justify-center gap-2 rounded-full font-semibold tracking-[-0.01em] disabled:cursor-not-allowed disabled:opacity-40 ${SIZES[size]} ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      {icon}
      {children}
    </button>
  );
}

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly label: string;
  readonly children: ReactNode;
  readonly tone?: 'surface' | 'soft' | 'ink';
}

const TONES = { surface: 'bg-surface text-text border border-border', soft: 'bg-surface-2 text-text', ink: 'bg-ink text-on-ink' } as const;

/** A round 48 px icon-only button. The label is read by screen readers. */
export function IconButton({ label, children, tone = 'surface', className = '', type = 'button', ...props }: IconButtonProps) {
  return (
    <button
      type={type}
      aria-label={label}
      title={label}
      className={`press inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-full ${TONES[tone]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
