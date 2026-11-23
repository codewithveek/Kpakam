import { useId } from 'react';

export interface ToggleRowProps {
  readonly label: string;
  readonly help?: string;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
}

/** An accessible switch row (role="switch"), 48 px tall. */
export function ToggleRow({ label, help, checked, onChange }: ToggleRowProps) {
  const id = useId();
  return (
    <div className="flex items-start justify-between gap-4">
      <div>
        <p id={`${id}-label`} className="font-semibold">
          {label}
        </p>
        {help !== undefined && (
          <p id={`${id}-help`} className="text-sm text-muted">
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
        className={`relative inline-flex h-12 w-20 shrink-0 items-center rounded-full border-2 transition-colors ${checked ? 'border-primary bg-primary' : 'border-border bg-surface-2'}`}
      >
        <span className={`ml-1 inline-block h-9 w-9 rounded-full bg-surface shadow transition-transform ${checked ? 'translate-x-8' : ''}`} />
        <span className="sr-only">{checked ? 'On' : 'Off'}</span>
      </button>
    </div>
  );
}
