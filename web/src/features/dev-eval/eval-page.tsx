import enGolden from '@contract/golden/en.jsonl?raw';
import pcmGolden from '@contract/golden/pcm.jsonl?raw';
import { useState } from 'react';
import { useServices } from '@/app/services';
import type { InferenceTarget } from '@/inference/clients/inference-client';
import { draftResponseSchema } from '@/inference/domain/draft';
import { parseGolden, runGolden, type EvalReport } from '@/inference/eval/run-golden';
import { PROMPTS } from '@/inference/prompt-library';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';

const SETS = { en: enGolden, pcm: pcmGolden } as const;
const validate = (data: unknown) => draftResponseSchema.safeParse(data).success;

/**
 * Dev-only (`/dev/eval`): runs a golden set through Gemini Nano in this Chrome (or the cloud)
 * and shows first-pass validity, field accuracy, and latency. On-device evals need a machine
 * with the local model, so they run here, not in CI.
 */
export function EvalPage() {
  const { inference } = useServices();
  const [set, setSet] = useState<keyof typeof SETS>('en');
  const [target, setTarget] = useState<InferenceTarget>('device');
  const [progress, setProgress] = useState<string | null>(null);
  const [report, setReport] = useState<EvalReport | null>(null);

  async function run() {
    setReport(null);
    await inference.prepareDeviceModel();
    const result = await runGolden(parseGolden(SETS[set]), {
      client: inference,
      target,
      prompt: PROMPTS.parse,
      validate,
      onProgress: (done, total) => setProgress(`${done}/${total}`),
    });
    setReport(result);
    setProgress(null);
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-bold">Golden-set evaluation</h1>
      <Card className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col text-sm font-semibold">
          Set
          <select className="min-h-12 rounded-lg border border-border bg-bg px-3" value={set} onChange={(e) => setSet(e.target.value as keyof typeof SETS)}>
            <option value="en">English (60)</option>
            <option value="pcm">Pidgin (40)</option>
          </select>
        </label>
        <label className="flex flex-col text-sm font-semibold">
          Target
          <select className="min-h-12 rounded-lg border border-border bg-bg px-3" value={target} onChange={(e) => setTarget(e.target.value as InferenceTarget)}>
            <option value="device">On device</option>
            <option value="cloud">Cloud</option>
          </select>
        </label>
        <Button onClick={() => void run()} disabled={progress !== null}>
          {progress ?? 'Run'}
        </Button>
      </Card>
      {report !== null && (
        <Card>
          <pre className="overflow-x-auto text-sm">{JSON.stringify(report, null, 2)}</pre>
        </Card>
      )}
    </div>
  );
}
