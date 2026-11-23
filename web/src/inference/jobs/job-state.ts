/** AI job lifecycle (PRD "Pending AI job queue"). */
export type JobState = 'queued' | 'running' | 'succeeded' | 'failed' | 'cancelled';

const TRANSITIONS: Readonly<Record<JobState, readonly JobState[]>> = {
  queued: ['running', 'cancelled'],
  // running → queued: the job was interrupted (offline, rate limit, tab closed).
  running: ['succeeded', 'failed', 'queued'],
  // failed → queued: the trader taps "Try again".
  failed: ['queued', 'cancelled'],
  succeeded: [],
  cancelled: [],
};

/** Thrown on an illegal transition. This is a bug, not an expected failure. */
export class IllegalJobTransition extends Error {
  constructor(from: JobState, to: JobState) {
    super(`Illegal AI job transition: ${from} → ${to}`);
    this.name = 'IllegalJobTransition';
  }
}

/** Returns true when `from → to` is allowed. */
export function canTransition(from: JobState, to: JobState): boolean {
  return TRANSITIONS[from].includes(to);
}

/**
 * Validates a transition.
 * @throws IllegalJobTransition when the transition is not allowed.
 */
export function transition(from: JobState, to: JobState): JobState {
  if (!canTransition(from, to)) throw new IllegalJobTransition(from, to);
  return to;
}

/** Terminal states never run again. */
export function isTerminal(state: JobState): boolean {
  return TRANSITIONS[state].length === 0;
}
