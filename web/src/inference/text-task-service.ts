import { err, ok, type Result } from '@/shared/result';
import type { AiEventSink } from './ai-event';
import type { InferenceClient } from './clients/inference-client';
import { fillDataPrompt, type PromptVersion } from './domain/prompt-builder';
import { routeJob, type QueueReason, type RouteDecision } from './domain/route-decision';
import type { SignalSource } from './signals';

export interface TextTaskDeps {
  readonly client: InferenceClient;
  readonly signals: SignalSource;
  readonly events: AiEventSink;
  readonly prompts: { readonly summary: PromptVersion; readonly insights: PromptVersion };
  readonly now?: () => number;
}

export type InsightsError = { readonly type: 'held'; readonly queueReason: QueueReason } | { readonly type: 'failed' };

interface TaskLog {
  readonly task: 'summary' | 'insights';
  readonly decision: RouteDecision;
  readonly started: number;
  readonly source: 'on_device' | 'in_cloud' | 'none';
  readonly promptVersion: string;
}

const clock = (deps: TextTaskDeps) => deps.now ?? (() => performance.now());

function log(deps: TextTaskDeps, entry: TaskLog): void {
  const valid = entry.source !== 'none';
  deps.events.record({
    task: entry.task,
    target: entry.decision.target,
    reason: entry.decision.reason,
    source: entry.source,
    latencyMs: Math.round(clock(deps)() - entry.started),
    valid,
    attempts: valid ? 1 : 0,
    escalated: false,
    queuedMs: null,
    promptVersion: entry.promptVersion,
  });
}

/**
 * Writes a one or two sentence summary of today's totals. Runs on device by default.
 * @returns The sentence, or null when it cannot run now (the dashboard numbers still show).
 */
async function summariseDay(deps: TextTaskDeps, totals: unknown): Promise<string | null> {
  const env = await deps.signals.snapshot();
  const prompt = fillDataPrompt(deps.prompts.summary.body, { placeholder: 'totals', data: totals });
  const config = { ...env.config, maxDeviceChars: Number.MAX_SAFE_INTEGER };
  const decision = routeJob({ task: 'summary', chars: prompt.length, languageScore: 0, approved: false, ...env }, config);
  if (decision.target === 'queue') return null;
  const started = clock(deps)();
  const result = await deps.client.generate(decision.target, { prompt, output: 'text' });
  log(deps, { task: 'summary', decision, started, source: result.ok ? result.value.source : 'none', promptVersion: deps.prompts.summary.id });
  return result.ok ? result.value.text.trim() : null;
}

/**
 * Streams the weekly report from the cloud (`LONG_CONTEXT`). With data-saver on,
 * it waits for the trader's "Send now" (`approved`).
 */
async function weeklyInsights(
  deps: TextTaskDeps,
  input: { week: unknown; approved: boolean; onChunk: (text: string) => void },
): Promise<Result<string, InsightsError>> {
  const env = await deps.signals.snapshot();
  const decision = routeJob({ task: 'insights', chars: 0, languageScore: 0, approved: input.approved, ...env }, env.config);
  if (decision.target === 'queue' && decision.queueReason !== null) return err({ type: 'held', queueReason: decision.queueReason });
  const started = clock(deps)();
  const prompt = fillDataPrompt(deps.prompts.insights.body, { placeholder: 'week', data: input.week });
  const result = await deps.client.stream({ prompt, output: 'text' }, input.onChunk);
  log(deps, { task: 'insights', decision, started, source: result.ok ? 'in_cloud' : 'none', promptVersion: deps.prompts.insights.id });
  return result.ok ? ok(result.value.text) : err({ type: 'failed' });
}

/** Short on-device summaries and streamed cloud insights (PRD hybrid table rows 6–7). */
export function createTextTaskService(deps: TextTaskDeps) {
  return {
    summariseDay: (totals: unknown) => summariseDay(deps, totals),
    weeklyInsights: (week: unknown, options: { approved: boolean; onChunk: (text: string) => void }) => weeklyInsights(deps, { week, ...options }),
  };
}

export type TextTaskService = ReturnType<typeof createTextTaskService>;
