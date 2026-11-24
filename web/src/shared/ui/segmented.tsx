import { useId } from 'react';

export interface SegmentedProps<T extends string> {
  readonly label: string;
  readonly value: T;
  readonly options: readonly { readonly value: T; readonly label: string }[];
  readonly onChange: (value: T) => void;
}

/** A segmented control on native radio inputs. The chosen option is a black pill. */
export function Segmented<T extends string>({ label, value, options, onChange }: SegmentedProps<T>) {
  const name = useId();
  return (
    <fieldset className="flex min-w-0 gap-1 rounded-full bg-surface-2 p-1">
      <legend className="sr-only">{label}</legend>
      {options.map((option) => (
        <label
          key={option.value}
          className={`press relative flex min-h-11 flex-1 cursor-pointer items-center justify-center rounded-full px-3 text-center text-[14px] font-semibold has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-[var(--color-focus)] ${value === option.value ? 'bg-ink text-on-ink' : 'text-muted'}`}
        >
          <input type="radio" className="sr-only" name={name} value={option.value} checked={value === option.value} onChange={() => onChange(option.value)} />
          {option.label}
        </label>
      ))}
    </fieldset>
  );
}
