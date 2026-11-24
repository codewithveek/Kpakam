import type { BatteryState, DeviceModelState, LocalSpeechState, RouteConfig, VoiceLanguage } from './domain/route-decision';

/** A snapshot of everything routing depends on, read just before each job runs. */
export interface EnvironmentSnapshot {
  readonly online: boolean;
  readonly cloudEnabled: boolean;
  readonly dataSaver: boolean;
  readonly battery: BatteryState | null;
  readonly deviceModel: DeviceModelState;
  readonly localSpeech: LocalSpeechState;
  readonly voiceLanguage: VoiceLanguage;
  readonly config: RouteConfig;
}

/** Reads the current environment. Implemented in the app layer from platform adapters. */
export interface SignalSource {
  snapshot(): Promise<EnvironmentSnapshot>;
}
