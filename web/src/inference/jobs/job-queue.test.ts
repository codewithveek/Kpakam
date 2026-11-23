import { describe, expect, it, vi } from 'vitest';
import type { ParseOutcome } from '../parse-service';
import { createJobQueue, visibleJobs } from './job-queue';
import { createIndexedDbJobStore } from './job-store';
import { estimatedSizeKb } from './job';

const DRAFTS: ParseOutcome = {
  type: 'drafts',
  output: {
    response: {
      drafts: [{ kind: 'sale', item: 'rice', quantity: null, unit: null, totalNaira: 10, paidNaira: null, customerName: null, confidence: 1 }],
      needsClarification: null,
      transcript: null,
    },
    source: 'on_device',
    reason: 'DEFAULT_DEVICE',
    confidence: 'normal',
    promptVersion: 'parse-v1',
  },
};
const QUEUED = (queueReason: 'OFFLINE' | 'DATA_SAVER'): ParseOutcome => ({
  type: 'queued',
  queueReason,
  decision: { target: 'queue', reason: 'IMAGE_INPUT', confidence: 'normal', queueReason },
});

let dbCount = 0;
function setup(outcomes: ParseOutcome[]) {
  const parse = vi.fn((_input: unknown, _opts: { approved: boolean; queuedMs: number | null }) => Promise.resolve(outcomes.shift() ?? DRAFTS));
  dbCount += 1;
  const store = createIndexedDbJobStore(`test-queue-${dbCount}`);
  const queue = createJobQueue({ store, parser: { parse }, toBase64: () => Promise.resolve('AA==') });
  return { queue, parse, store };
}

describe('job queue', () => {
  it('stores and runs a job, keeping the drafts for review', async () => {
    const { queue } = setup([DRAFTS]);
    const job = await queue.submit({ type: 'text', text: 'sold rice' });
    expect(job.state).toBe('succeeded');
    expect(job.output?.source).toBe('on_device');
    expect(job.journalIds).toHaveLength(1);
    expect(visibleJobs(await queue.list())).toHaveLength(1);
    await queue.markReviewed(job.id);
    expect(visibleJobs(await queue.list())).toHaveLength(0);
  });

  it('keeps an offline job queued and drains it on reconnect', async () => {
    const { queue, parse } = setup([QUEUED('OFFLINE'), DRAFTS]);
    const job = await queue.submit({ type: 'text', text: 'sold rice' });
    expect(job).toMatchObject({ state: 'queued', queueReason: 'OFFLINE' });
    await queue.drain();
    const [after] = await queue.list();
    expect(after?.state).toBe('succeeded');
    expect(parse.mock.calls[1]?.[1].queuedMs).toBeGreaterThanOrEqual(0);
  });

  it('does not drain data-saver jobs until Send now', async () => {
    const { queue, parse } = setup([QUEUED('DATA_SAVER'), DRAFTS]);
    const job = await queue.submit({ type: 'image', blob: new Blob(['x']), mimeType: 'image/webp' });
    await queue.drain();
    expect(parse).toHaveBeenCalledTimes(1);
    const sent = await queue.approve(job.id);
    expect(sent.state).toBe('succeeded');
    expect(parse.mock.calls[1]?.[1].approved).toBe(true);
    expect(parse.mock.calls[1]?.[0]).toMatchObject({ type: 'image', base64: 'AA==' });
  });

  it('retries failed jobs and cancels unwanted ones', async () => {
    const failed: ParseOutcome = {
      type: 'failed',
      problems: ['Blocked'],
      decision: { target: 'cloud', reason: 'LANGUAGE', confidence: 'normal', queueReason: null },
    };
    const { queue } = setup([failed, DRAFTS, QUEUED('OFFLINE')]);
    const job = await queue.submit({ type: 'text', text: 'x' });
    expect(job).toMatchObject({ state: 'failed', error: 'Blocked' });
    expect((await queue.retry(job.id)).state).toBe('succeeded');
    const other = await queue.submit({ type: 'text', text: 'y' });
    await queue.cancel(other.id);
    expect(visibleJobs(await queue.list()).map((j) => j.id)).toEqual([job.id]);
  });

  it('recovers jobs left running and re-queues on unexpected errors', async () => {
    const { queue, store, parse } = setup([]);
    parse.mockRejectedValueOnce(new Error('boom'));
    const job = await queue.submit({ type: 'text', text: 'x' });
    expect(job).toMatchObject({ state: 'queued', error: 'boom' });
    await store.update(job.id, { state: 'running' });
    await queue.recover();
    expect((await store.get(job.id))?.state).toBe('queued');
  });

  it('notifies subscribers and estimates upload size', async () => {
    const { queue } = setup([DRAFTS]);
    const seen: number[] = [];
    const stop = queue.subscribe((jobs) => seen.push(jobs.length));
    await queue.submit({ type: 'text', text: 'x' });
    stop();
    expect(seen.at(-1)).toBe(1);
    expect(estimatedSizeKb({ type: 'image', blob: new Blob([new Uint8Array(150 * 1024)]), mimeType: 'image/webp' })).toBe(208);
  });

  it('ignores a duplicate idempotency key', async () => {
    const { store } = setup([]);
    const base = {
      id: 'a',
      idempotencyKey: 'same',
      task: 'parse_text',
      input: { type: 'text', text: 'x' },
      capturedAt: '',
      state: 'queued',
      decision: { target: 'queue', reason: 'DEFAULT_DEVICE', confidence: 'normal', queueReason: null },
      queueReason: null,
      approved: false,
      attempts: 0,
      output: null,
      journalIds: [],
      reviewed: false,
      error: null,
      updatedAt: '',
    } as const;
    await store.add(base);
    const second = await store.add({ ...base, id: 'b' });
    expect(second.id).toBe('a');
    expect(await store.list()).toHaveLength(1);
    await expect(store.update('a', { state: 'succeeded' })).rejects.toThrow('Illegal');
  });
});
