// Cloud evaluation over the golden sets (PRD "AI evaluation gate").
// Usage (from web/): npm run eval:cloud -- [--set en|pcm|all] [--limit 10] [--update-baseline]
// Needs web/.env.local with the Firebase web config and APP_CHECK_DEBUG_TOKEN (a registered
// App Check debug token, from CI secrets or your shell; never committed).
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseArgs } from 'node:util';
import { initializeApp } from 'firebase/app';
import { CustomProvider, initializeAppCheck } from 'firebase/app-check';
import { draftValidator } from '../../packages/contract/scripts/draft-validator.mjs';
import { createFirebaseInferenceClient } from '../src/inference/clients/firebase-inference-client';
import { parsePromptFile } from '../src/inference/domain/prompt-builder';
import { parseGolden, runGolden, type EvalReport } from '../src/inference/eval/run-golden';
import { DEFAULT_CONFIG } from '../src/platform/firebase/remote-config-adapter';

const CONTRACT = join(import.meta.dirname, '../../packages/contract');
const RESULTS = join(CONTRACT, 'golden/results');
const BASELINE = join(RESULTS, 'baseline-cloud.json');

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined || value === '') throw new Error(`Missing ${name}. See web/.env.example and scripts/eval-golden.ts.`);
  return value;
}

/** Exchanges a registered App Check debug token for an App Check token (REST, as the SDK's debug provider does). */
async function exchangeDebugToken(spec: { projectId: string; appId: string; apiKey: string; debugToken: string }) {
  const url = `https://firebaseappcheck.googleapis.com/v1/projects/${spec.projectId}/apps/${spec.appId}:exchangeDebugToken?key=${spec.apiKey}`;
  const response = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ debugToken: spec.debugToken }) });
  if (!response.ok) throw new Error(`App Check debug exchange failed: ${response.status}`);
  const body = (await response.json()) as { token: string; ttl: string };
  return { token: body.token, expireTimeMillis: Date.now() + Number.parseFloat(body.ttl) * 1000 };
}

function initFirebase() {
  if (existsSync('.env.local')) process.loadEnvFile('.env.local');
  const config = { apiKey: env('VITE_FIREBASE_API_KEY'), projectId: env('VITE_FIREBASE_PROJECT_ID'), appId: env('VITE_FIREBASE_APP_ID') };
  const app = initializeApp(config);
  const debugToken = env('APP_CHECK_DEBUG_TOKEN');
  initializeAppCheck(app, {
    provider: new CustomProvider({ getToken: () => exchangeDebugToken({ ...config, debugToken }) }),
    isTokenAutoRefreshEnabled: false,
  });
  return app;
}

function printReport(set: string, report: EvalReport): void {
  console.log(`\n${set} (${report.total} sentences, ${report.promptVersion}, ${report.target})`);
  console.log(`  valid on first pass  ${report.validPct}%`);
  console.log(`  correct              ${report.correctPct}%`);
  for (const [field, pct] of Object.entries(report.fieldPct)) console.log(`  ${field.padEnd(20)} ${pct}%`);
  console.log(`  latency p50/p95      ${report.p50Ms} / ${report.p95Ms} ms`);
  if (report.failures.length > 0) console.log(`  not correct: ${report.failures.map((f) => `${f.id} (${f.reason})`).join(', ')}`);
}

function gate(reports: Record<string, EvalReport>): boolean {
  if (!existsSync(BASELINE)) return true;
  const baseline = JSON.parse(readFileSync(BASELINE, 'utf8')) as Record<string, EvalReport>;
  let ok = true;
  for (const [set, report] of Object.entries(reports)) {
    const before = baseline[set]?.correctPct;
    if (before !== undefined && report.correctPct < before) {
      console.error(`REGRESSION ${set}: ${report.correctPct}% < baseline ${before}%`);
      ok = false;
    }
  }
  return ok;
}

async function main(): Promise<void> {
  const { values } = parseArgs({
    options: { set: { type: 'string', default: 'all' }, limit: { type: 'string' }, 'update-baseline': { type: 'boolean', default: false } },
  });
  const app = initFirebase();
  const client = createFirebaseInferenceClient({
    app,
    cloudModelName: () => process.env['CLOUD_MODEL_NAME'] ?? DEFAULT_CONFIG.cloudModelName,
    useLimitedUseAppCheckTokens: false,
  });
  const prompt = parsePromptFile(readFileSync(join(CONTRACT, `prompts/${DEFAULT_CONFIG.promptVersionParse}.md`), 'utf8'));
  const sets = values.set === 'all' ? ['en', 'pcm'] : [values.set];
  const reports: Record<string, EvalReport> = {};
  for (const set of sets) {
    const lines = parseGolden(readFileSync(join(CONTRACT, `golden/${set}.jsonl`), 'utf8')).slice(
      0,
      values.limit === undefined ? undefined : Number(values.limit),
    );
    reports[set] = await runGolden(lines, { client, target: 'cloud', prompt, validate: draftValidator() });
    printReport(set, reports[set]);
  }
  mkdirSync(RESULTS, { recursive: true });
  writeFileSync(join(RESULTS, `cloud-${prompt.id}-latest.json`), `${JSON.stringify(reports, null, 2)}\n`);
  if (values['update-baseline']) writeFileSync(BASELINE, `${JSON.stringify(reports, null, 2)}\n`);
  process.exit(gate(reports) ? 0 : 1);
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(2);
});
