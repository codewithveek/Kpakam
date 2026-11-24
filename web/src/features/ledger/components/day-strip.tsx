import { useEffect, useRef } from 'react';
import { MESSAGES } from '@/shared/messages/en';
import { addDays, type LagosDay } from '@/shared/time/lagos';

const DAYS = 14;
const WEEKDAY = new Intl.DateTimeFormat('en-NG', { timeZone: 'Africa/Lagos', weekday: 'short' });
const DATE = new Intl.DateTimeFormat('en-NG', { timeZone: 'Africa/Lagos', day: 'numeric' });
const at = (day: LagosDay) => new Date(`${day}T12:00:00+01:00`);

export interface DayStripProps {
  readonly today: LagosDay;
  readonly value: LagosDay;
  readonly onChange: (day: LagosDay) => void;
}

/** The last two weeks as a horizontal strip of day chips (radio semantics, keyboard friendly). */
export function DayStrip({ today, value, onChange }: DayStripProps) {
  const days = Array.from({ length: DAYS }, (_, i) => addDays(today, i - DAYS + 1));
  const strip = useRef<HTMLFieldSetElement>(null);
  const selected = useRef<HTMLLabelElement>(null);
  useEffect(() => {
    // Scroll only the strip (scrollIntoView would also scroll the page sideways).
    const container = strip.current;
    const chip = selected.current;
    if (container === null || chip === null) return;
    container.scrollLeft = chip.offsetLeft - container.clientWidth / 2 + chip.clientWidth / 2;
  }, [value]);
  return (
    <fieldset ref={strip} className="no-scrollbar relative -mx-4 flex min-w-0 gap-2 overflow-x-auto px-4 py-1">
      <legend className="sr-only">{MESSAGES.ledger.pickDay}</legend>
      {days.map((day) => {
        const active = day === value;
        return (
          <label
            key={day}
            ref={active ? selected : undefined}
            className={`press flex min-h-[76px] w-[58px] shrink-0 cursor-pointer flex-col items-center justify-center rounded-full text-center has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-[var(--color-focus)] ${active ? 'bg-ink text-on-ink' : 'bg-surface text-text'}`}
          >
            <input type="radio" name="history-day" className="sr-only" checked={active} onChange={() => onChange(day)} />
            <span className={`text-[12px] font-semibold ${active ? 'text-on-ink-muted' : 'text-muted'}`}>
              {day === today ? MESSAGES.dashboard.title : WEEKDAY.format(at(day))}
            </span>
            <span className="amount text-[18px] font-bold">{DATE.format(at(day))}</span>
          </label>
        );
      })}
    </fieldset>
  );
}
