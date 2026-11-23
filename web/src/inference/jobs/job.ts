import type { DraftResponse } from '../domain/draft';
import type { AiTask, QueueReason, RouteDecision } from '../domain/route-decision';
import type { InferenceSourceKind } from '../clients/inference-client';
import type { JobState } from './job-state';

/** Input that waits in the queue. Blobs are stored as-is in IndexedDB. */
export type JobInput =
  | { readonly type: 'text'; readonly text: string }
  | { readonly type: 'image'; readonly blob: Blob; readonly mimeType: string }
  | { readonly type: 'audio'; readonly blob: Blob; readonly mimeType: string; readonly mixedHeard?: boolean };

/** What a finished parse job produced, ready for the trader to review. */
export interface JobOutput {
  readonly response: DraftResponse;
  readonly source: InferenceSourceKind;
  readonly reason: RouteDecision['reason'];
  readonly confidence: RouteDecision['confidence'];
  readonly promptVersion: string;
}

/** A pending AI job, durable across reloads. */
export interface AiJob {
  readonly id: string;
  readonly idempotencyKey: string;
  readonly task: AiTask;
  readonly input: JobInput;
  readonly capturedAt: string;
  readonly state: JobState;
  readonly decision: RouteDecision;
  readonly queueReason: QueueReason | null;
  readonly approved: boolean;
  readonly attempts: number;
  readonly output: JobOutput | null;
  /** One journal id per draft, fixed when the job succeeds, so saving twice is a no-op. */
  readonly journalIds: readonly string[];
  readonly reviewed: boolean;
  readonly error: string | null;
  readonly updatedAt: string;
}

/** Estimated upload size in KB, shown before the trader taps "Send now". */
export function estimatedSizeKb(input: JobInput): number {
  const bytes = input.type === 'text' ? new TextEncoder().encode(input.text).length + 3000 : input.blob.size * 1.37 + 3000;
  return Math.max(1, Math.round(bytes / 1024));
}
