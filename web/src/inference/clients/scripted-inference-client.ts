import { err, ok, type Result } from '@/shared/result';
import type { DeviceModelState } from '../domain/route-decision';
import type { InferenceClient, InferenceError, InferenceRequest, InferenceSuccess, InferenceTarget } from './inference-client';

/** A canned reply for one call. */
export type ScriptedReply = { readonly text: string } | { readonly error: InferenceError };

export interface ScriptedClientOptions {
  readonly deviceModel?: DeviceModelState;
  readonly device?: readonly ScriptedReply[];
  readonly cloud?: readonly ScriptedReply[];
  /** Used when a script runs out: builds a reply from the request. */
  readonly fallback?: (target: InferenceTarget, request: InferenceRequest) => ScriptedReply;
}

/**
 * Deterministic `InferenceClient` for unit tests and the Playwright e2e build
 * (`VITE_FAKE_INFERENCE=true`). Never used in production builds.
 */
export function createScriptedInferenceClient(options: ScriptedClientOptions = {}) {
  const device = [...(options.device ?? [])];
  const queues: Record<InferenceTarget, ScriptedReply[]> = { device, cloud: [...(options.cloud ?? [])], prefer_device: device };
  const calls: { target: InferenceTarget; request: InferenceRequest }[] = [];

  function reply(target: InferenceTarget, request: InferenceRequest): Result<InferenceSuccess, InferenceError> {
    calls.push({ target, request });
    const next = queues[target].shift() ?? options.fallback?.(target, request);
    if (next === undefined) return err({ type: 'CloudFailed', message: 'No scripted reply' });
    if ('error' in next) return err(next.error);
    return ok({ text: next.text, source: target === 'cloud' ? 'in_cloud' : 'on_device' });
  }

  const client: InferenceClient = {
    deviceModelState: () => Promise.resolve(options.deviceModel ?? 'available'),
    prepareDeviceModel: () => Promise.resolve(),
    generate: (target, request) => Promise.resolve(reply(target, request)),
    stream: (request, onChunk) => {
      const result = reply('cloud', request);
      if (result.ok) onChunk(result.value.text);
      return Promise.resolve(result);
    },
  };
  return { client, calls };
}
