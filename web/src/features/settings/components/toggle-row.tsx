import { useId } from 'react';

export interface ToggleRowProps {
  readonly label: string;
  readonly help?: string;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
}

/** An accessible switch row (role="switch"). The thumb shows a check when on, so state never relies on colour. */
export function ToggleRow({ label, help, checked, onChange }: ToggleRowProps) {
  const id = useId();
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3">
      <div className="min-w-0">
        <p id={`${id}-label`} className="text-[15px] font-semibold">
          {label}
        </p>
        {help !== undefined && (
          <p id={`${id}-help`} className="mt-0.5 text-[13px] leading-snug text-muted">
            {help}
          </p>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={`${id}-label`}
        {...(help === undefined ? {} : { 'aria-describedby': `${id}-help` })}
        onClick={() => onChange(!checked)}
        className={`press relative inline-flex h-8 w-[52px] shrink-0 items-center rounded-full transition-colors duration-200 before:absolute before:-inset-2 ${checked ? 'bg-primary' : 'bg-border'}`}
      >
        <span
          className={`flex h-6 w-6 items-center justify-center rounded-full bg-white text-[11px] font-bold text-primary shadow transition-transform duration-200 ease-[var(--ease-out)] ${checked ? 'translate-x-6' : 'translate-x-1'}`}
          aria-hidden="true"
        >
          {checked ? '✓' : ''}
        </span>
        <span className="sr-only">{checked ? 'On' : 'Off'}</span>
      </button>
    </div>
  );
}
