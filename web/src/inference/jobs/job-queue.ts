import { newUlid } from '@/shared/ids/ulid';
import type { AiTask } from '../domain/route-decision';
import type { ParseInput, ParseOutcome, ParseService } from '../parse-service';
import type { AiJob, JobInput } from './job';
import type { JobStore } from './job-store';

export interface JobQueueDeps {
  readonly store: JobStore;
  readonly parser: Pick<ParseService, 'parse'>;
  readonly toBase64: (blob: Blob) => Promise<string>;
  readonly clock?: () => Date;
}

type Listener = (jobs: readonly AiJob[]) => void;

/** Mutable queue state shared by the functions below. */
interface QueueContext {
  readonly deps: JobQueueDeps;
  readonly clock: () => Date;
  readonly listeners: Set<Listener>;
  readonly running: Set<string>;
  draining: Promise<void> | null;
}

function taskOf(input: JobInput): AiTask {
  if (input.type === 'image') return 'parse_image';
  return input.type === 'audio' ? 'parse_audio' : 'parse_text';
}

async function notify(ctx: QueueContext): Promise<void> {
  const jobs = await ctx.deps.store.list();
  for (const listener of ctx.listeners) listener(jobs);
}

async function toParseInput(ctx: QueueContext, input: JobInput): Promise<ParseInput> {
  if (input.type === 'text') return input;
  const base64 = await ctx.deps.toBase64(input.blob);
  if (input.type === 'audio') return { type: 'audio', mimeType: input.mimeType, base64, mixedHeard: input.mixedHeard === true };
  return { type: input.type, mimeType: input.mimeType, base64 };
}

function settle(ctx: QueueContext, change: { job: AiJob; outcome: ParseOutcome }): Promise<AiJob> {
  const { job, outcome } = change;
  const attempts = job.attempts + 1;
  switch (outcome.type) {
    case 'drafts': {
      const { output } = outcome;
      const decision = { ...job.decision, reason: output.reason, target: output.source === 'on_device' ? ('device' as const) : ('cloud' as const) };
      const journalIds = output.response.drafts.map(() => newUlid());
      return ctx.deps.store.update(job.id, { state: 'succeeded', output, journalIds, decision, queueReason: null, attempts, error: null });
    }
    case 'queued':
      return ctx.deps.store.update(job.id, { state: 'queued', decision: outcome.decision, queueReason: outcome.queueReason, attempts });
    case 'failed':
      return ctx.deps.store.update(job.id, { state: 'failed', decision: outcome.decision, error: outcome.problems.join('; '), attempts });
  }
}

/** Runs one queued job: queued → running → succeeded | failed | queued. */
async function run(ctx: QueueContext, job: AiJob): Promise<AiJob> {
  if (ctx.running.has(job.id) || job.state !== 'queued') return job;
  ctx.running.add(job.id);
  try {
    const started = await ctx.deps.store.update(job.id, { state: 'running' });
    await notify(ctx);
    const queuedMs = job.attempts === 0 ? null : Math.max(0, ctx.clock().getTime() - Date.parse(job.capturedAt));
    const outcome = await ctx.deps.parser.parse(await toParseInput(ctx, job.input), { approved: job.approved, queuedMs });
    return await settle(ctx, { job: started, outcome });
  } catch (error) {
    return await ctx.deps.store.update(job.id, { state: 'queued', error: error instanceof Error ? error.message : 'Unknown error' });
  } finally {
    ctx.running.delete(job.id);
    await notify(ctx);
  }
}

function newJob(input: JobInput, now: string): AiJob {
  const id = newUlid();
  return {
    id,
    idempotencyKey: id,
    task: taskOf(input),
    input,
    capturedAt: now,
    state: 'queued',
    decision: { target: 'queue', reason: 'DEFAULT_DEVICE', confidence: 'normal', queueReason: null },
    queueReason: null,
    approved: false,
    attempts: 0,
    output: null,
    journalIds: [],
    reviewed: false,
    error: null,
    updatedAt: now,
  };
}

async function drainOnce(ctx: QueueContext): Promise<void> {
  for (const job of await ctx.deps.store.list()) {
    const held = job.queueReason === 'DATA_SAVER' && !job.approved;
    if (job.state === 'queued' && !held) await run(ctx, job);
  }
}

async function recover(ctx: QueueContext): Promise<void> {
  for (const job of await ctx.deps.store.list()) {
    if (job.state === 'running') await ctx.deps.store.update(job.id, { state: 'queued' });
  }
  await notify(ctx);
}

async function updateAndNotify(ctx: QueueContext, change: { id: string; patch: Parameters<JobStore['update']>[1] }): Promise<AiJob> {
  const job = await ctx.deps.store.update(change.id, change.patch);
  await notify(ctx);
  return job;
}

/**
 * The pending AI job queue (PRD "Pending AI job queue"). Every submission is stored
 * first, so nothing is lost if the tab closes mid-call. A job runs at most once per
 * idempotency key; queued jobs drain when a connection returns or on "Send now".
 */
export function createJobQueue(deps: JobQueueDeps) {
  const ctx: QueueContext = { deps, clock: deps.clock ?? (() => new Date()), listeners: new Set(), running: new Set(), draining: null };
  return {
    /** Stores a new job and runs it once now. Returns the job after that first run. */
    async submit(input: JobInput): Promise<AiJob> {
      const job = await deps.store.add(newJob(input, ctx.clock().toISOString()));
      await notify(ctx);
      return run(ctx, job);
    },
    /** Runs every queued job that data-saver is not holding. Safe to call often. */
    drain(): Promise<void> {
      ctx.draining ??= drainOnce(ctx).finally(() => (ctx.draining = null));
      return ctx.draining;
    },
    /** Recovers jobs left `running` by a closed tab. Call once at startup. */
    recover: () => recover(ctx),
    /** "Send now" on a job held by data-saver. */
    approve: async (id: string) => run(ctx, await deps.store.update(id, { approved: true })),
    /** "Try again" on a failed job. */
    retry: async (id: string) => run(ctx, await deps.store.update(id, { state: 'queued', error: null })),
    cancel: async (id: string): Promise<void> => {
      await updateAndNotify(ctx, { id, patch: { state: 'cancelled' } });
    },
    /** The trader saved or discarded every draft of a finished job. */
    markReviewed: async (id: string): Promise<void> => {
      await updateAndNotify(ctx, { id, patch: { reviewed: true } });
    },
    subscribe(listener: Listener): () => void {
      ctx.listeners.add(listener);
      void deps.store.list().then(listener);
      return () => ctx.listeners.delete(listener);
    },
    list: () => deps.store.list(),
  };
}

export type JobQueue = ReturnType<typeof createJobQueue>;

/** Jobs the trader still needs to see: waiting, running, failed, or finished but not reviewed. */
export function visibleJobs(jobs: readonly AiJob[]): AiJob[] {
  return jobs.filter((job) => job.state !== 'cancelled' && !(job.state === 'succeeded' && job.reviewed));
}
