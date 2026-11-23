import { describe, expect, it } from 'vitest';
import type { AiEventRecord } from './ai-event';
import { createScriptedInferenceClient } from './clients/scripted-inference-client';
import { PROMPTS } from './prompt-library';
import type { EnvironmentSnapshot } from './signals';
import { createTextTaskService } from './text-task-service';

const ENV: EnvironmentSnapshot = {
  online: true,
  cloudEnabled: true,
  dataSaver: false,
  battery: null,
  deviceModel: 'available',
  localSpeech: 'unavailable',
  voiceLanguage: 'en',
  config: { maxDeviceChars: 280, languageThreshold: 0.35, minBattery: 0.15 },
};

function setup(env: Partial<EnvironmentSnapshot>, replies: { device?: string[]; cloud?: string[] }) {
  const { client, calls } = createScriptedInferenceClient({
    device: (replies.device ?? []).map((text) => ({ text })),
    cloud: (replies.cloud ?? []).map((text) => ({ text })),
  });
  const events: AiEventRecord[] = [];
  const service = createTextTaskService({
    client,
    signals: { snapshot: () => Promise.resolve({ ...ENV, ...env }) },
    events: { record: (e) => events.push(e) },
    prompts: PROMPTS,
    now: () => 0,
  });
  return { service, calls, events };
}

describe('text tasks', () => {
  it('summarises the day on device, sending totals only', async () => {
    const { service, calls, events } = setup({}, { device: [' Good day. '] });
    expect(await service.summariseDay({ sales: 45000 })).toBe('Good day.');
    expect(calls[0]?.target).toBe('device');
    expect(calls[0]?.request.prompt).toContain('{"sales":45000}');
    expect(events[0]).toMatchObject({ task: 'summary', source: 'on_device', valid: true });
  });

  it('skips the summary when nothing can run', async () => {
    const { service } = setup({ online: false, deviceModel: 'unavailable' }, {});
    expect(await service.summariseDay({})).toBeNull();
  });

  it('streams weekly insights from the cloud, and holds them for data-saver', async () => {
    const chunks: string[] = [];
    const ok = setup({}, { cloud: ['Best sellers: rice.'] });
    expect(await ok.service.weeklyInsights({}, { approved: false, onChunk: (t) => chunks.push(t) })).toEqual({ ok: true, value: 'Best sellers: rice.' });
    expect(chunks).toEqual(['Best sellers: rice.']);
    const held = setup({ dataSaver: true }, {});
    expect(await held.service.weeklyInsights({}, { approved: false, onChunk: () => undefined })).toEqual({
      ok: false,
      error: { type: 'held', queueReason: 'DATA_SAVER' },
    });
    const failed = setup({}, {});
    expect(await failed.service.weeklyInsights({}, { approved: true, onChunk: () => undefined })).toEqual({ ok: false, error: { type: 'failed' } });
  });
});
