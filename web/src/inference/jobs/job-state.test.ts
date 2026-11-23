import { describe, expect, it } from 'vitest';
import { canTransition, IllegalJobTransition, isTerminal, transition, type JobState } from './job-state';

const ALL: JobState[] = ['queued', 'running', 'succeeded', 'failed', 'cancelled'];
const LEGAL = new Set(['queued>running', 'queued>cancelled', 'running>succeeded', 'running>failed', 'running>queued', 'failed>queued', 'failed>cancelled']);

describe('AI job state machine', () => {
  it.each(ALL.flatMap((from) => ALL.map((to) => [from, to] as const)))('%s → %s', (from, to) => {
    const legal = LEGAL.has(`${from}>${to}`);
    expect(canTransition(from, to)).toBe(legal);
    if (legal) expect(transition(from, to)).toBe(to);
    else expect(() => transition(from, to)).toThrow(IllegalJobTransition);
  });

  it('marks succeeded and cancelled as terminal', () => {
    expect(ALL.filter(isTerminal)).toEqual(['succeeded', 'cancelled']);
  });
});
