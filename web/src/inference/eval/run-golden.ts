import { scoreGolden, type GoldenField, type GoldenScore } from '@contract/scripts/score-golden.mjs';
import type { InferenceClient, InferenceTarget } from '../clients/inference-client';
import { stripCodeFences } from '../domain/draft-validation';
import { buildParsePrompt, type PromptVersion } from '../domain/prompt-builder';

/** One line of golden/en.jsonl or golden/pcm.jsonl. */
export interface GoldenLine {
  readonly id: string;
  readonly text: string;
  readonly tags: readonly string[];
  readonly expected: unknown;
}

export interface EvalReport {
  readonly promptVersion: string;
  readonly target: InferenceTarget;
  readonly total: number;
  readonly validPct: number;
  readonly correctPct: number;
  readonly fieldPct: Readonly<Partial<Record<GoldenField, number>>>;
  readonly p50Ms: number;
  readonly p95Ms: number;
  readonly failures: readonly { id: string; reason: string }[];
}

/** Parses golden JSONL text. */
export function parseGolden(jsonl: string): GoldenLine[] {
  return jsonl
    .trim()
    .split('\n')
    .map((line) => JSON.parse(line) as GoldenLine);
}

function parseOutput(text: string): unknown {
  try {
    return JSON.parse(stripCodeFences(text)) as unknown;
  } catch {
    return null;
  }
}

function percentile(values: readonly number[], p: number): number {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))] ?? 0;
}

function summarise(spec: {
  scores: readonly GoldenScore[];
  latencies: readonly number[];
}): Pick<EvalReport, 'validPct' | 'correctPct' | 'fieldPct' | 'p50Ms' | 'p95Ms'> {
  const pct = (n: number, d: number) => (d === 0 ? 0 : Math.round((n / d) * 1000) / 10);
  const fields = new Set(spec.scores.flatMap((s) => Object.keys(s.fields) as GoldenField[]));
  const fieldPct: Partial<Record<GoldenField, number>> = {};
  for (const field of fields) {
    const applicable = spec.scores.map((s) => s.fields[field]).filter((v) => v !== null);
    fieldPct[field] = pct(applicable.filter(Boolean).length, applicable.length);
  }
  return {
    validPct: pct(spec.scores.filter((s) => s.valid).length, spec.scores.length),
    correctPct: pct(spec.scores.filter((s) => s.correct).length, spec.scores.length),
    fieldPct,
    p50Ms: percentile(spec.latencies, 50),
    p95Ms: percentile(spec.latencies, 95),
  };
}

export interface EvalOptions {
  readonly client: InferenceClient;
  readonly target: InferenceTarget;
  readonly prompt: PromptVersion;
  readonly validate: (data: unknown) => boolean;
  readonly onProgress?: (done: number, total: number) => void;
}

/**
 * Runs each golden sentence once through the prompt on one target and scores the first
 * output (PRD "AI evaluation gate": valid on first pass, field accuracy, latency).
 */
export async function runGolden(lines: readonly GoldenLine[], options: EvalOptions): Promise<EvalReport> {
  const scores: GoldenScore[] = [];
  const latencies: number[] = [];
  const failures: { id: string; reason: string }[] = [];
  for (const line of lines) {
    const started = performance.now();
    const result = await options.client.generate(options.target, { prompt: buildParsePrompt(options.prompt.body, line.text), output: 'json' });
    latencies.push(Math.round(performance.now() - started));
    const score = scoreGolden(line.expected, result.ok ? parseOutput(result.value.text) : null, options.validate);
    scores.push(score);
    if (!score.correct) failures.push({ id: line.id, reason: result.ok ? (score.valid ? 'wrong fields' : 'invalid output') : result.error.type });
    options.onProgress?.(scores.length, lines.length);
  }
  return { promptVersion: options.prompt.id, target: options.target, total: lines.length, failures, ...summarise({ scores, latencies }) };
}
