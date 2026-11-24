/** Jobs the router can place (routing.md). */
export type AiTask = 'parse_text' | 'parse_image' | 'parse_audio' | 'summary' | 'insights';
export type RoutableTask = Exclude<AiTask, 'parse_audio'>;

export type DeviceModelState = 'available' | 'downloadable' | 'downloading' | 'unavailable';

export type ReasonCode =
  | 'IMAGE_INPUT'
  | 'LONG_CONTEXT'
  | 'DEVICE_UNAVAILABLE'
  | 'LANGUAGE'
  | 'TOO_LONG'
  | 'LOW_BATTERY'
  | 'DEFAULT_DEVICE'
  | 'VALIDATION_ESCALATION'
  | 'ROUTER_FALLBACK'
  | 'SPEECH_DEVICE'
  | 'SPEECH_CLOUD'
  | 'SPEECH_MIXED';

export type QueueReason = 'OFFLINE' | 'DATA_SAVER' | 'CLOUD_DISABLED' | 'RATE_LIMITED';
export type RouteTarget = 'device' | 'cloud' | 'queue';
export type Confidence = 'normal' | 'low';

/** The router's answer: where to run, and why. */
export interface RouteDecision {
  readonly target: RouteTarget;
  readonly reason: ReasonCode;
  readonly confidence: Confidence;
  readonly queueReason: QueueReason | null;
}

export interface BatteryState {
  readonly level: number;
  readonly charging: boolean;
}

/** Everything the router looks at. Plain data, so cases are shareable across apps. */
export interface RouteSignals {
  readonly task: RoutableTask;
  readonly chars: number;
  readonly languageScore: number;
  readonly online: boolean;
  readonly cloudEnabled: boolean;
  readonly dataSaver: boolean;
  readonly approved: boolean;
  readonly deviceModel: DeviceModelState;
  readonly battery: BatteryState | null;
}

/** Remote Config values the router reads. */
export interface RouteConfig {
  readonly maxDeviceChars: number;
  readonly languageThreshold: number;
  readonly minBattery: number;
}

function decide(reason: ReasonCode, choice: Pick<RouteDecision, 'target'> & Partial<RouteDecision>): RouteDecision {
  return { reason, confidence: 'normal', queueReason: null, ...choice };
}

function blockedReason(signals: RouteSignals): QueueReason {
  return signals.online ? 'CLOUD_DISABLED' : 'OFFLINE';
}

function cloudReachable(signals: RouteSignals): boolean {
  return signals.online && signals.cloudEnabled;
}

/** Steps 1–2: large cloud-only jobs that data-saver may hold. */
function routeLargeJob(signals: RouteSignals, reason: ReasonCode): RouteDecision {
  if (!cloudReachable(signals)) return decide(reason, { target: 'queue', queueReason: blockedReason(signals) });
  if (signals.dataSaver && !signals.approved) return decide(reason, { target: 'queue', queueReason: 'DATA_SAVER' });
  return decide(reason, { target: 'cloud' });
}

/** Steps 4–5: prefer cloud, fall back to device with low confidence. */
function preferCloud(signals: RouteSignals, reason: ReasonCode): RouteDecision {
  return cloudReachable(signals) ? decide(reason, { target: 'cloud' }) : decide(reason, { target: 'device', confidence: 'low' });
}

function isLowBattery(signals: RouteSignals, config: RouteConfig): boolean {
  const { battery } = signals;
  return battery !== null && !battery.charging && battery.level < config.minBattery;
}

/**
 * Picks where an AI job runs. Pure: same signals and config, same decision.
 * Implements the ordered checks in packages/contract/routing.md.
 * @returns The target (`device`, `cloud`, or `queue`) with a reason code.
 */
export function routeJob(signals: RouteSignals, config: RouteConfig): RouteDecision {
  if (signals.task === 'parse_image') return routeLargeJob(signals, 'IMAGE_INPUT');
  if (signals.task === 'insights') return routeLargeJob(signals, 'LONG_CONTEXT');
  if (signals.deviceModel !== 'available') {
    return cloudReachable(signals)
      ? decide('DEVICE_UNAVAILABLE', { target: 'cloud' })
      : decide('DEVICE_UNAVAILABLE', { target: 'queue', queueReason: blockedReason(signals) });
  }
  if (signals.languageScore > config.languageThreshold) return preferCloud(signals, 'LANGUAGE');
  if (signals.chars > config.maxDeviceChars) return preferCloud(signals, 'TOO_LONG');
  if (isLowBattery(signals, config) && cloudReachable(signals)) return decide('LOW_BATTERY', { target: 'cloud' });
  return decide('DEFAULT_DEVICE', { target: 'device' });
}

/**
 * Decides what to do after the device output failed validation twice.
 * @returns Cloud (`VALIDATION_ESCALATION`) when reachable, else a queue decision.
 */
export function escalate(signals: Pick<RouteSignals, 'online' | 'cloudEnabled'>): RouteDecision {
  if (signals.online && signals.cloudEnabled) return decide('VALIDATION_ESCALATION', { target: 'cloud' });
  return decide('VALIDATION_ESCALATION', { target: 'queue', queueReason: signals.online ? 'CLOUD_DISABLED' : 'OFFLINE' });
}

/** `auto` (default): Kpakam works out the language, even for mixed English, Pidgin and Yoruba. */
export type VoiceLanguage = 'auto' | 'en' | 'pcm_yo';
export type LocalSpeechState = 'available' | 'downloadable' | 'downloading' | 'unavailable';

export interface SpeechSignals {
  readonly voiceLanguage: VoiceLanguage;
  readonly localSpeech: LocalSpeechState;
  readonly online: boolean;
  readonly cloudEnabled: boolean;
  /** True after the mixed-speech check failed: the phone's English-only transcript can't be trusted. */
  readonly mixedHeard?: boolean;
}

/**
 * Picks where speech becomes text (routing.md "Speech router").
 * `device` means on-device speech recognition, then the text router.
 */
export function routeSpeech(signals: SpeechSignals): RouteDecision {
  const reachable = signals.online && signals.cloudEnabled;
  const blocked = signals.online ? 'CLOUD_DISABLED' : 'OFFLINE';
  if (signals.mixedHeard === true) {
    return reachable ? decide('SPEECH_MIXED', { target: 'cloud' }) : decide('SPEECH_MIXED', { target: 'queue', queueReason: blocked });
  }
  if (signals.voiceLanguage !== 'pcm_yo' && signals.localSpeech === 'available') return decide('SPEECH_DEVICE', { target: 'device' });
  if (reachable) return decide('SPEECH_CLOUD', { target: 'cloud' });
  return decide('SPEECH_CLOUD', { target: 'queue', queueReason: blocked });
}

/** Below this recogniser confidence, the phone's transcript is not trusted (routing.md). */
export const MIN_SPEECH_CONFIDENCE = 0.5;

/**
 * Mixed-speech check (routing.md, auto mode): should the recorded clip go to the cloud model
 * instead of trusting the phone's English-only transcript?
 */
export function isMixedSpeech(input: { transcript: string; confidence: number | null; languageScore: number; threshold: number }): boolean {
  if (input.transcript.trim() === '') return true;
  if (input.languageScore > input.threshold) return true;
  return input.confidence !== null && input.confidence < MIN_SPEECH_CONFIDENCE;
}
