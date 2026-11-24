import type { FirebaseApp } from 'firebase/app';
import type { GenerativeModel, HybridParams } from 'firebase/ai';
import { err, ok, type Result } from '@/shared/result';
import type { DeviceModelState } from '../domain/route-decision';
import type { InferenceClient, InferenceError, InferenceRequest, InferenceSuccess, InferenceTarget } from './inference-client';
import { modelDraftSchema } from './model-schema';

type AiModule = typeof import('firebase/ai');
type Output = InferenceRequest['output'];

interface LanguageModelApi {
  availability(options: object): Promise<string>;
}

const MAX_OUTPUT_TOKENS: Readonly<Record<Output, number>> = { json: 600, text: 400 };
/** Fresh objects each time: the SDK mutates and stores what it is given. */
function languageOptions() {
  return {
    expectedInputs: [{ type: 'text' as const, languages: ['en'] }],
    expectedOutputs: [{ type: 'text' as const, languages: ['en'] }],
  };
}
const DEVICE_STATES = new Set<string>(['available', 'downloadable', 'downloading']);

export interface FirebaseInferenceOptions {
  readonly app: FirebaseApp;
  readonly cloudModelName: () => string;
  readonly useLimitedUseAppCheckTokens: boolean;
}

interface ClientContext {
  readonly options: FirebaseInferenceOptions;
  readonly models: Map<string, GenerativeModel>;
  sdk: Promise<AiModule> | null;
}

function languageModelApi(): LanguageModelApi | null {
  const candidate: unknown = Reflect.get(globalThis, 'LanguageModel');
  return candidate !== null && (typeof candidate === 'function' || typeof candidate === 'object') ? (candidate as LanguageModelApi) : null;
}

function errorCode(error: unknown): string {
  return typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : '';
}

/** Maps SDK errors to our expected-failure union. */
export function toInferenceError(error: unknown): InferenceError {
  const code = errorCode(error);
  const message = error instanceof Error ? error.message : String(error);
  if (code === 'api-not-enabled' || code === 'unsupported' || /on-device|LanguageModel/i.test(message)) return { type: 'DeviceModelUnavailable' };
  if (code === 'fetch-error' || /network|failed to fetch/i.test(message)) return { type: 'Offline' };
  if (/429|quota|RESOURCE_EXHAUSTED/i.test(message)) return { type: 'QuotaExceeded' };
  if (/block|safety/i.test(message)) return { type: 'Blocked', message };
  return { type: 'CloudFailed', message: message.slice(0, 200) };
}

const MODES: Readonly<Record<InferenceTarget, (ai: AiModule) => HybridParams['mode']>> = {
  device: (ai) => ai.InferenceMode.ONLY_ON_DEVICE,
  cloud: (ai) => ai.InferenceMode.ONLY_IN_CLOUD,
  prefer_device: (ai) => ai.InferenceMode.PREFER_ON_DEVICE,
};

/** Kpakam owns routing: the router picks ONLY_ON_DEVICE or ONLY_IN_CLOUD. PREFER_ON_DEVICE is the safety net only. */
function hybridParams(ai: AiModule, spec: { target: InferenceTarget; output: Output; cloudModel: string }): HybridParams {
  const schema = modelDraftSchema();
  const json = spec.output === 'json';
  return {
    mode: MODES[spec.target](ai),
    inCloudParams: {
      model: spec.cloudModel,
      generationConfig: {
        maxOutputTokens: MAX_OUTPUT_TOKENS[spec.output],
        ...(json ? { responseMimeType: 'application/json', responseJsonSchema: schema } : {}),
      },
    },
    onDeviceParams: {
      createOptions: languageOptions(),
      ...(json ? { promptOptions: { responseConstraint: schema } } : {}),
    },
  };
}

/** Returns a cached model for target + output + current cloud model name. `firebase/ai` loads lazily. */
async function modelFor(ctx: ClientContext, spec: { target: InferenceTarget; output: Output }): Promise<GenerativeModel> {
  const cloudModel = ctx.options.cloudModelName();
  const key = `${spec.target}:${spec.output}:${cloudModel}`;
  const cached = ctx.models.get(key);
  if (cached !== undefined) return cached;
  ctx.sdk ??= import('firebase/ai');
  const ai = await ctx.sdk;
  const service = ai.getAI(ctx.options.app, { backend: new ai.GoogleAIBackend(), useLimitedUseAppCheckTokens: ctx.options.useLimitedUseAppCheckTokens });
  const model = ai.getGenerativeModel(service, hybridParams(ai, { ...spec, cloudModel }));
  ctx.models.set(key, model);
  return model;
}

function partsOf(request: InferenceRequest) {
  if (request.media === undefined) return request.prompt;
  return [request.prompt, { type: 'inlineData' as const, inlineData: { mimeType: request.media.mimeType, data: request.media.base64 } }];
}

async function generate(ctx: ClientContext, call: { target: InferenceTarget; request: InferenceRequest }): Promise<Result<InferenceSuccess, InferenceError>> {
  try {
    const model = await modelFor(ctx, { target: call.target, output: call.request.output });
    const result = await model.generateContent(partsOf(call.request));
    return ok({ text: result.response.text(), source: result.response.inferenceSource === 'on_device' ? 'on_device' : 'in_cloud' });
  } catch (error) {
    return err(toInferenceError(error));
  }
}

async function stream(
  ctx: ClientContext,
  call: { request: InferenceRequest; onChunk: (text: string) => void },
): Promise<Result<InferenceSuccess, InferenceError>> {
  try {
    const result = await (await modelFor(ctx, { target: 'cloud', output: 'text' })).generateContentStream(partsOf(call.request));
    let text = '';
    for await (const chunk of result.stream) {
      text += chunk.text();
      call.onChunk(text);
    }
    return ok({ text, source: 'in_cloud' });
  } catch (error) {
    return err(toInferenceError(error));
  }
}

async function deviceModelState(): Promise<DeviceModelState> {
  const api = languageModelApi();
  if (api === null) return 'unavailable';
  try {
    const state = await api.availability(languageOptions());
    return DEVICE_STATES.has(state) ? (state as DeviceModelState) : 'unavailable';
  } catch {
    return 'unavailable';
  }
}

/**
 * `InferenceClient` over Firebase AI Logic hybrid inference (Gemini Nano in Chrome via the
 * Prompt API, Gemini in the cloud). `firebase/ai` loads on first use, never at startup.
 */
export function createFirebaseInferenceClient(options: FirebaseInferenceOptions): InferenceClient {
  const ctx: ClientContext = { options, models: new Map(), sdk: null };
  return {
    deviceModelState,
    async prepareDeviceModel(onProgress) {
      try {
        await (await modelFor(ctx, { target: 'device', output: 'json' })).initializeDeviceModel(onProgress);
      } catch {
        // Not supported here: the router sends work to the cloud (DEVICE_UNAVAILABLE).
      }
    },
    generate: (target, request) => generate(ctx, { target, request }),
    stream: (request, onChunk) => stream(ctx, { request, onChunk }),
  };
}
