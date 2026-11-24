/** Kpakam shows and groups all dates in Lagos time (UTC+1, no daylight saving). */
export const LAGOS_TIME_ZONE = 'Africa/Lagos';
const LAGOS_OFFSET_MINUTES = 60;

const DAY_FORMAT = new Intl.DateTimeFormat('en-CA', {
  timeZone: LAGOS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const TIME_FORMAT = new Intl.DateTimeFormat('en-NG', {
  timeZone: LAGOS_TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});
const DATE_FORMAT = new Intl.DateTimeFormat('en-NG', {
  timeZone: LAGOS_TIME_ZONE,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

/** A calendar day in Lagos, as `YYYY-MM-DD`. */
export type LagosDay = string;

/** Returns the Lagos calendar day of an instant. */
export function lagosDay(date: Date): LagosDay {
  return DAY_FORMAT.format(date);
}

/** Formats an instant as ISO 8601 with the Lagos offset, e.g. 2026-11-24T09:41:00+01:00. */
export function toLagosIso(date: Date): string {
  const shifted = new Date(date.getTime() + LAGOS_OFFSET_MINUTES * 60_000);
  return `${shifted.toISOString().slice(0, 19)}+01:00`;
}

/** Adds whole days to a Lagos day. */
export function addDays(day: LagosDay, days: number): LagosDay {
  const date = new Date(`${day}T12:00:00+01:00`);
  date.setUTCDate(date.getUTCDate() + days);
  return lagosDay(date);
}

/** Returns the Monday that starts the Lagos week containing `day`. */
export function weekStart(day: LagosDay): LagosDay {
  const date = new Date(`${day}T12:00:00+01:00`);
  const weekday = (date.getUTCDay() + 6) % 7;
  return addDays(day, -weekday);
}

/** Formats the time of day, e.g. 9:41 am. */
export function formatLagosTime(iso: string): string {
  return TIME_FORMAT.format(new Date(iso));
}

/** Formats a short date, e.g. Tue, 24 Nov. */
export function formatLagosDate(day: LagosDay): string {
  return DATE_FORMAT.format(new Date(`${day}T12:00:00+01:00`));
}
