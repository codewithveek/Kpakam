import type { Result } from '@/shared/result';
import type { DeviceModelState } from '../domain/route-decision';

/** `prefer_device` is the PREFER_ON_DEVICE safety-net model, used only when routing fails. */
export type InferenceTarget = 'device' | 'cloud' | 'prefer_device';
export type InferenceSourceKind = 'on_device' | 'in_cloud';

/** One model call. `json` asks for output constrained to the draft schema. */
export interface InferenceRequest {
  readonly prompt: string;
  readonly output: 'json' | 'text';
  readonly media?: { readonly mimeType: string; readonly base64: string };
}

export interface InferenceSuccess {
  readonly text: string;
  readonly source: InferenceSourceKind;
}

/** Expected failures. Exceptions are reserved for bugs. */
export type InferenceError =
  | { readonly type: 'DeviceModelUnavailable' }
  | { readonly type: 'Offline' }
  | { readonly type: 'QuotaExceeded' }
  | { readonly type: 'Blocked'; readonly message: string }
  | { readonly type: 'CloudFailed'; readonly message: string };

/**
 * Our interface over Firebase AI Logic. Services depend on this, never on the SDK,
 * so tests use fakes and the experimental hybrid SDK can change without touching business code.
 */
export interface InferenceClient {
  /** Current state of the on-device model. Never triggers a download. */
  deviceModelState(): Promise<DeviceModelState>;
  /**
   * Prepares the on-device model. Must run during or after a user gesture when a
   * download is needed (Chrome user-activation rule).
   */
  prepareDeviceModel(onProgress?: (fraction: number) => void): Promise<void>;
  /** Runs one request on the given target (ONLY_ON_DEVICE, ONLY_IN_CLOUD, or the PREFER_ON_DEVICE safety net). */
  generate(target: InferenceTarget, request: InferenceRequest): Promise<Result<InferenceSuccess, InferenceError>>;
  /** Streams a long text response from the cloud (weekly insights). */
  stream(request: InferenceRequest, onChunk: (text: string) => void): Promise<Result<InferenceSuccess, InferenceError>>;
}
