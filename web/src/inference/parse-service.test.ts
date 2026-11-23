import parseFile from '@contract/prompts/parse-v1.md?raw';
import retryFile from '@contract/prompts/parse-retry-v1.md?raw';
import receiptFile from '@contract/prompts/receipt-v1.md?raw';
import voiceFile from '@contract/prompts/voice-v1.md?raw';
import { describe, expect, it } from 'vitest';
import type { AiEventRecord } from './ai-event';
import { createScriptedInferenceClient, type ScriptedClientOptions } from './clients/scripted-inference-client';
import { parsePromptFile } from './domain/prompt-builder';
import { createRateLimiter } from './jobs/rate-limiter';
import { createParseService } from './parse-service';
import type { EnvironmentSnapshot } from './signals';

const PROMPTS = {
  parse: parsePromptFile(parseFile),
  parseRetry: parsePromptFile(retryFile),
  voice: parsePromptFile(voiceFile),
  receipt: parsePromptFile(receiptFile),
};
const ENV: EnvironmentSnapshot = {
  online: true,
  cloudEnabled: true,
  dataSaver: false,
  battery: null,
  deviceModel: 'available',
  localSpeech: 'available',
  voiceLanguage: 'en',
  config: { maxDeviceChars: 280, languageThreshold: 0.35, minBattery: 0.15 },
};
const GOOD = JSON.stringify({ drafts: [{ kind: 'sale', item: 'rice', totalNaira: 45000, confidence: 0.9 }], needsClarification: null });
const BAD = JSON.stringify({ drafts: [{ kind: 'repayment', totalNaira: 5000, confidence: 0.9 }] });
const OPTS = { approved: false, queuedMs: null };

function setup(script: ScriptedClientOptions, env: Partial<EnvironmentSnapshot> = {}, limit = 10) {
  const { client, calls } = createScriptedInferenceClient(script);
  const events: AiEventRecord[] = [];
  const service = createParseService({
    client,
    signals: { snapshot: () => Promise.resolve({ ...ENV, ...env }) },
    prompts: PROMPTS,
    events: { record: (e) => events.push(e) },
    rateLimiter: createRateLimiter(() => limit),
    now: () => 0,
  });
  return { service, calls, events };
}

describe('parse service', () => {
  it('parses a plain English sale on device', async () => {
    const { service, calls, events } = setup({ device: [{ text: GOOD }] });
    const outcome = await service.parseText('sold 3 bags of rice 45k', OPTS);
    expect(outcome.type === 'drafts' && outcome.output).toMatchObject({ source: 'on_device', reason: 'DEFAULT_DEVICE', promptVersion: 'parse-v1' });
    expect(calls.map((c) => c.target)).toEqual(['device']);
    expect(events[0]).toMatchObject({ source: 'on_device', valid: true, attempts: 1, escalated: false });
    expect(JSON.stringify(events)).not.toContain('rice');
  });

  it('retries on device with the validation errors, then succeeds', async () => {
    const { service, calls } = setup({ device: [{ text: BAD }, { text: GOOD }] });
    const outcome = await service.parseText('Tunde paid 5k', OPTS);
    expect(outcome.type).toBe('drafts');
    expect(calls[1]?.request.prompt).toContain('customerName is required');
  });

  it('escalates to the cloud after two invalid device outputs', async () => {
    const { service, calls, events } = setup({ device: [{ text: 'nope' }, { text: BAD }], cloud: [{ text: GOOD }] });
    const outcome = await service.parseText('sold rice 45k', OPTS);
    expect(outcome.type === 'drafts' && outcome.output.reason).toBe('VALIDATION_ESCALATION');
    expect(calls.map((c) => c.target)).toEqual(['device', 'device', 'cloud']);
    expect(events.map((e) => e.escalated)).toEqual([true, true]);
  });

  it('queues the escalation when offline', async () => {
    const { service } = setup({ device: [{ text: 'x' }, { text: 'y' }] }, { online: false });
    const outcome = await service.parseText('sold rice 45k', OPTS);
    expect(outcome).toMatchObject({ type: 'queued', queueReason: 'OFFLINE' });
  });

  it('escalates when the device model throws', async () => {
    const { service, calls } = setup({ device: [{ error: { type: 'DeviceModelUnavailable' } }], cloud: [{ text: GOOD }] });
    expect((await service.parseText('sold rice 45k', OPTS)).type).toBe('drafts');
    expect(calls.map((c) => c.target)).toEqual(['device', 'cloud']);
  });

  it('sends Pidgin to the cloud', async () => {
    const { service, calls } = setup({ cloud: [{ text: GOOD }] });
    const outcome = await service.parseText('Mama Nkechi don pay the 3k wey she dey owe', OPTS);
    expect(outcome.type === 'drafts' && outcome.output).toMatchObject({ source: 'in_cloud', reason: 'LANGUAGE' });
    expect(calls.map((c) => c.target)).toEqual(['cloud']);
  });

  it('parses Pidgin on device with low confidence when offline', async () => {
    const { service } = setup({ device: [{ text: GOOD }] }, { online: false });
    const outcome = await service.parseText('Mama Nkechi don pay the 3k wey she dey owe', OPTS);
    expect(outcome.type === 'drafts' && outcome.output.confidence).toBe('low');
  });

  it('queues when the device model is missing and the phone is offline', async () => {
    const { service, calls } = setup({}, { online: false, deviceModel: 'unavailable' });
    expect(await service.parseText('sold rice', OPTS)).toMatchObject({ type: 'queued', queueReason: 'OFFLINE' });
    expect(calls).toHaveLength(0);
  });

  it('queues cloud calls over the rate limit', async () => {
    const { service } = setup({ cloud: [{ text: GOOD }] }, { deviceModel: 'unavailable' }, 0);
    expect(await service.parseText('sold rice', OPTS)).toMatchObject({ type: 'queued', queueReason: 'RATE_LIMITED' });
  });

  it('maps cloud offline and quota errors to queue reasons', async () => {
    const offline = setup({ cloud: [{ error: { type: 'Offline' } }] }, { deviceModel: 'unavailable' });
    expect(await offline.service.parseText('x', OPTS)).toMatchObject({ queueReason: 'OFFLINE' });
    const quota = setup({ cloud: [{ error: { type: 'QuotaExceeded' } }] }, { deviceModel: 'unavailable' });
    expect(await quota.service.parseText('x', OPTS)).toMatchObject({ queueReason: 'RATE_LIMITED' });
  });

  it('fails after two invalid cloud outputs or a blocked response', async () => {
    const invalid = setup({ cloud: [{ text: BAD }, { text: BAD }] }, { deviceModel: 'unavailable' });
    expect((await invalid.service.parseText('x', OPTS)).type).toBe('failed');
    const blocked = setup({ cloud: [{ error: { type: 'Blocked', message: 'safety' } }] }, { deviceModel: 'unavailable' });
    expect(await blocked.service.parseText('x', OPTS)).toMatchObject({ type: 'failed', problems: ['Blocked'] });
  });

  it('holds a receipt photo for data-saver until approved', async () => {
    const held = setup({ cloud: [{ text: GOOD }] }, { dataSaver: true });
    const input = { type: 'image', base64: 'AA==', mimeType: 'image/webp' } as const;
    expect(await held.service.parseMedia(input, OPTS)).toMatchObject({ type: 'queued', queueReason: 'DATA_SAVER' });
    const approved = await held.service.parseMedia(input, { approved: true, queuedMs: 1200 });
    expect(approved.type === 'drafts' && approved.output.reason).toBe('IMAGE_INPUT');
    expect(held.calls[0]?.request.media?.mimeType).toBe('image/webp');
    expect(held.events.at(-1)?.queuedMs).toBe(1200);
  });

  it('sends voice clips to the cloud with the voice prompt, and retries with media', async () => {
    const withTranscript = JSON.stringify({ drafts: [{ kind: 'sale', totalNaira: 1, confidence: 1 }], transcript: 'I sell am' });
    const { service, calls } = setup({ cloud: [{ text: 'bad' }, { text: withTranscript }] });
    const outcome = await service.parse({ type: 'audio', base64: 'AA==', mimeType: 'audio/webm' }, OPTS);
    expect(outcome.type === 'drafts' && outcome.output.response.transcript).toBe('I sell am');
    expect(outcome.type === 'drafts' && outcome.output.promptVersion).toBe('voice-v1');
    expect(calls[1]?.request.media).toBeDefined();
  });

  it('uses the PREFER_ON_DEVICE safety net when reading signals throws', async () => {
    const { client, calls } = createScriptedInferenceClient({ device: [{ text: GOOD }] });
    const service = createParseService({
      client,
      signals: { snapshot: () => Promise.reject(new Error('Battery API exploded')) },
      prompts: PROMPTS,
      events: { record: () => undefined },
      rateLimiter: createRateLimiter(() => 10),
      now: () => 0,
    });
    const outcome = await service.parseText('sold rice 45k', OPTS);
    expect(outcome.type === 'drafts' && outcome.output).toMatchObject({ reason: 'ROUTER_FALLBACK', confidence: 'low' });
    expect(calls.map((c) => c.target)).toEqual(['prefer_device']);
  });

  it('sends a clip online with SPEECH_MIXED when the phone heard mixed languages', async () => {
    const { service, calls } = setup({ cloud: [{ text: GOOD }] });
    const outcome = await service.parse({ type: 'audio', base64: 'AA==', mimeType: 'audio/webm', mixedHeard: true }, OPTS);
    expect(outcome.type === 'drafts' && outcome.output.reason).toBe('SPEECH_MIXED');
    expect(calls.map((c) => c.target)).toEqual(['cloud']);
  });

  it('keeps a mixed-language clip until the network returns', async () => {
    const { service } = setup({}, { online: false });
    expect(await service.parse({ type: 'audio', base64: 'AA==', mimeType: 'audio/webm', mixedHeard: true }, OPTS)).toMatchObject({
      type: 'queued',
      queueReason: 'OFFLINE',
      decision: { reason: 'SPEECH_MIXED' },
    });
  });

  it('queues voice clips offline', async () => {
    const { service } = setup({}, { online: false });
    expect(await service.parse({ type: 'audio', base64: 'AA==', mimeType: 'audio/webm' }, OPTS)).toMatchObject({ type: 'queued', queueReason: 'OFFLINE' });
  });
});
