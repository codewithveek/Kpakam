import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'soft';
type Size = 'md' | 'lg' | 'icon';

const VARIANTS: Readonly<Record<Variant, string>> = {
  primary:
    'bg-primary text-on-primary shadow-sm hover-fine:bg-primary-strong disabled:bg-surface-2 disabled:text-muted disabled:shadow-none disabled:opacity-100',
  secondary: 'bg-surface text-text shadow-card',
  soft: 'bg-device-soft text-device',
  ghost: 'bg-transparent text-text hover-fine:bg-surface-2',
  danger: 'bg-danger-soft text-danger',
};

const SIZES: Readonly<Record<Size, string>> = {
  md: 'min-h-12 px-4 text-[15px]',
  lg: 'min-h-14 px-6 text-base',
  icon: 'h-12 w-12',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly variant?: Variant;
  readonly size?: Size;
  readonly icon?: ReactNode;
}

/** Button with a 48 px minimum touch target and press feedback (scale 0.97). */
export function Button({ variant = 'primary', size = 'md', icon, className = '', children, type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`press inline-flex shrink-0 items-center justify-center gap-2 rounded-full font-semibold disabled:cursor-not-allowed disabled:opacity-40 ${SIZES[size]} ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      {icon}
      {children}
    </button>
  );
}
