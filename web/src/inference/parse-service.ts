import { err, ok, type Result } from '@/shared/result';
import type { AiEventRecord, AiEventSink } from './ai-event';
import type { InferenceClient, InferenceError, InferenceRequest, InferenceSourceKind } from './clients/inference-client';
import { validateDraftOutput } from './domain/draft-validation';
import { scoreLanguage } from './domain/language-score';
import { buildMediaPrompt, buildParsePrompt, buildRetryPrompt, type PromptVersion } from './domain/prompt-builder';
import { escalate, routeJob, routeSpeech, type AiTask, type QueueReason, type RouteDecision } from './domain/route-decision';
import type { JobOutput } from './jobs/job';
import type { RateLimiter } from './jobs/rate-limiter';
import type { EnvironmentSnapshot, SignalSource } from './signals';

export interface ParsePrompts {
  readonly parse: PromptVersion;
  readonly parseRetry: PromptVersion;
  readonly voice: PromptVersion;
  readonly receipt: PromptVersion;
}

export interface ParseServiceDeps {
  readonly client: InferenceClient;
  readonly signals: SignalSource;
  readonly prompts: ParsePrompts;
  readonly events: AiEventSink;
  readonly rateLimiter: RateLimiter;
  readonly now?: () => number;
}

/** What happened to one parse attempt. */
export type ParseOutcome =
  | { readonly type: 'drafts'; readonly output: JobOutput }
  | { readonly type: 'queued'; readonly decision: RouteDecision; readonly queueReason: QueueReason }
  | { readonly type: 'failed'; readonly decision: RouteDecision; readonly problems: readonly string[] };

export type ParseInput =
  | { readonly type: 'text'; readonly text: string }
  | { readonly type: 'image' | 'audio'; readonly base64: string; readonly mimeType: string; readonly mixedHeard?: boolean };

export interface ParseOptions {
  readonly approved: boolean;
  readonly queuedMs: number | null;
}

/** One routed attempt: the decision plus what the event log needs. */
interface Run {
  readonly deps: ParseServiceDeps;
  readonly now: () => number;
  readonly decision: RouteDecision;
  readonly task: AiTask;
  readonly promptVersion: string;
  readonly started: number;
  readonly queuedMs: number | null;
}

/** The first request and how to build the retry request from a bad output. */
interface Requests {
  readonly first: InferenceRequest;
  readonly retry: (previousOutput: string, errors: readonly string[]) => InferenceRequest;
}

function log(run: Run, fields: Pick<AiEventRecord, 'source' | 'valid' | 'attempts' | 'escalated'>): void {
  run.deps.events.record({
    task: run.task,
    target: run.decision.target,
    reason: run.decision.reason,
    latencyMs: Math.max(0, Math.round(run.now() - run.started)),
    queuedMs: run.queuedMs,
    promptVersion: run.promptVersion,
    ...fields,
  });
}

function queued(run: Run, queueReason: QueueReason): ParseOutcome {
  log(run, { source: 'none', valid: false, attempts: 0, escalated: false });
  return { type: 'queued', decision: { ...run.decision, target: 'queue', queueReason }, queueReason };
}

function queueReasonFor(error: InferenceError): QueueReason | null {
  if (error.type === 'Offline') return 'OFFLINE';
  return error.type === 'QuotaExceeded' ? 'RATE_LIMITED' : null;
}

/** Validates one output. Returns drafts when valid, or the problems for the retry prompt. */
function check(run: Run, result: { text: string; source: InferenceSourceKind; attempt: number }): Result<ParseOutcome, readonly string[]> {
  const validated = validateDraftOutput(result.text);
  if (!validated.ok) return err(validated.error);
  log(run, { source: result.source, valid: true, attempts: result.attempt, escalated: run.decision.reason === 'VALIDATION_ESCALATION' });
  const { reason, confidence } = run.decision;
  return ok({ type: 'drafts', output: { response: validated.value, source: result.source, reason, confidence, promptVersion: run.promptVersion } });
}

function failed(run: Run, problems: readonly string[]): ParseOutcome {
  log(run, { source: 'in_cloud', valid: false, attempts: 2, escalated: false });
  return { type: 'failed', decision: run.decision, problems };
}

/** Cloud: one call, one retry with the validation errors. Offline or quota errors queue the job. */
async function runCloud(run: Run, requests: Requests): Promise<ParseOutcome> {
  if (!run.deps.rateLimiter.tryAcquire()) return queued(run, 'RATE_LIMITED');
  let request = requests.first;
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    const result = await run.deps.client.generate('cloud', request);
    if (!result.ok) {
      const reason = queueReasonFor(result.error);
      return reason === null ? failed(run, [result.error.type]) : queued(run, reason);
    }
    const checked = check(run, { text: result.value.text, source: result.value.source, attempt });
    if (checked.ok) return checked.value;
    request = requests.retry(result.value.text, checked.error);
  }
  return failed(run, ['Output did not match the schema twice']);
}

/** Device: one call, one retry with errors, then escalate to the cloud or queue (routing.md). */
async function runDevice(run: Run, input: { requests: Requests; env: EnvironmentSnapshot }): Promise<ParseOutcome> {
  let request = input.requests.first;
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    const result = await run.deps.client.generate('device', request);
    if (!result.ok) break;
    const checked = check(run, { text: result.value.text, source: 'on_device', attempt });
    if (checked.ok) return checked.value;
    request = input.requests.retry(result.value.text, checked.error);
  }
  log(run, { source: 'on_device', valid: false, attempts: 2, escalated: true });
  const next: Run = { ...run, decision: escalate(input.env), started: run.now() };
  if (next.decision.target === 'queue' && next.decision.queueReason !== null) return queued(next, next.decision.queueReason);
  return runCloud(next, input.requests);
}

const FALLBACK_DECISION: RouteDecision = { target: 'device', reason: 'ROUTER_FALLBACK', confidence: 'low', queueReason: null };

/** Safety net when routing throws: one PREFER_ON_DEVICE call, validated like any other (routing.md). */
async function runFallback(run: Run, requests: Requests): Promise<ParseOutcome> {
  const result = await run.deps.client.generate('prefer_device', requests.first);
  if (!result.ok) {
    const reason = queueReasonFor(result.error);
    return reason === null ? failed(run, [result.error.type]) : queued(run, reason);
  }
  const checked = check(run, { text: result.value.text, source: result.value.source, attempt: 1 });
  return checked.ok ? checked.value : failed(run, checked.error);
}

function textRequests(prompts: ParsePrompts, note: string): Requests {
  return {
    first: { prompt: buildParsePrompt(prompts.parse.body, note), output: 'json' },
    retry: (previousOutput, errors) => ({
      prompt: buildRetryPrompt({ parseBody: prompts.parse.body, retryBody: prompts.parseRetry.body, note, previousOutput, errors }),
      output: 'json',
    }),
  };
}

function mediaRequests(prompts: ParsePrompts, input: Extract<ParseInput, { type: 'image' | 'audio' }>): Requests {
  const media = { mimeType: input.mimeType, base64: input.base64 };
  const prompt = buildMediaPrompt((input.type === 'audio' ? prompts.voice : prompts.receipt).body, prompts.parse.body);
  return {
    first: { prompt, output: 'json', media },
    retry: (previous, errors) => ({
      prompt: `${prompt}\n\nYour last JSON:\n${previous.slice(0, 2000)}\n\n${prompts.parseRetry.body.replace('{{errors}}', errors.map((e) => `- ${e}`).join('\n'))}`,
      output: 'json',
      media,
    }),
  };
}

function startRun(deps: ParseServiceDeps, init: Pick<Run, 'decision' | 'task' | 'promptVersion' | 'queuedMs'>): Run {
  const now = deps.now ?? (() => performance.now());
  return { deps, now, started: now(), ...init };
}

/**
 * Parses one typed note (F1). Routes with the contract router, validates every output,
 * retries once on device with errors, then escalates to the cloud or queues.
 */
async function parseText(deps: ParseServiceDeps, input: { text: string; options: ParseOptions }): Promise<ParseOutcome> {
  const { text, options } = input;
  let env: EnvironmentSnapshot;
  let decision: RouteDecision;
  try {
    env = await deps.signals.snapshot();
    decision = routeJob({ task: 'parse_text', chars: text.length, languageScore: scoreLanguage(text), approved: options.approved, ...env }, env.config);
  } catch {
    return runFallback(
      startRun(deps, { decision: FALLBACK_DECISION, task: 'parse_text', promptVersion: deps.prompts.parse.id, queuedMs: options.queuedMs }),
      textRequests(deps.prompts, text),
    );
  }
  const run = startRun(deps, { decision, task: 'parse_text', promptVersion: deps.prompts.parse.id, queuedMs: options.queuedMs });
  if (decision.target === 'queue' && decision.queueReason !== null) return queued(run, decision.queueReason);
  const requests = textRequests(deps.prompts, text);
  return decision.target === 'device' ? runDevice(run, { requests, env }) : runCloud(run, requests);
}

/** Parses a receipt photo (S1) or a voice clip (F8) in the cloud; voice also returns the transcript. */
async function parseMedia(
  deps: ParseServiceDeps,
  input: { media: Extract<ParseInput, { type: 'image' | 'audio' }>; options: ParseOptions },
): Promise<ParseOutcome> {
  const env = await deps.signals.snapshot();
  const { media, options } = input;
  const audio = media.type === 'audio';
  const decision = audio
    ? routeSpeech({ ...env, localSpeech: 'unavailable', mixedHeard: media.mixedHeard === true })
    : routeJob({ task: 'parse_image', chars: 0, languageScore: 0, approved: options.approved, ...env }, env.config);
  const promptVersion = (audio ? deps.prompts.voice : deps.prompts.receipt).id;
  const run = startRun(deps, { decision, task: audio ? 'parse_audio' : 'parse_image', promptVersion, queuedMs: options.queuedMs });
  if (decision.target === 'queue' && decision.queueReason !== null) return queued(run, decision.queueReason);
  return runCloud(run, mediaRequests(deps.prompts, media));
}

/** Creates the parse use case: route → run → validate → retry → escalate. */
export function createParseService(deps: ParseServiceDeps) {
  return {
    parseText: (text: string, options: ParseOptions) => parseText(deps, { text, options }),
    parseMedia: (media: Extract<ParseInput, { type: 'image' | 'audio' }>, options: ParseOptions) => parseMedia(deps, { media, options }),
    parse: (input: ParseInput, options: ParseOptions) =>
      input.type === 'text' ? parseText(deps, { text: input.text, options }) : parseMedia(deps, { media: input, options }),
  };
}

export type ParseService = ReturnType<typeof createParseService>;
