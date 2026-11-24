import type { InferenceSourceKind } from './clients/inference-client';
import type { AiTask, RouteTarget } from './domain/route-decision';

/**
 * One routing outcome, stored in `aiEvents`. Codes and numbers only:
 * never the trader's text, names, or amounts.
 */
export interface AiEventRecord {
  readonly task: AiTask;
  readonly target: RouteTarget;
  readonly reason: string;
  readonly source: InferenceSourceKind | 'none';
  readonly latencyMs: number;
  readonly valid: boolean;
  readonly attempts: number;
  readonly escalated: boolean;
  readonly queuedMs: number | null;
  readonly promptVersion: string | null;
}

/** Where routing outcomes go. Fire-and-forget; must never block the trader. */
export interface AiEventSink {
  record(event: AiEventRecord): void;
}
