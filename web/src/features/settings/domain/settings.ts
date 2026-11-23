import type { VoiceLanguage } from '@/inference/domain/route-decision';

/** The trader's settings, stored at `users/{uid}.settings`. */
export interface TraderSettings {
  readonly dataSaver: boolean;
  readonly voiceLanguage: VoiceLanguage;
  readonly analyticsOptIn: boolean;
}

/** Defaults: data-saver off (PRD F7), English voice, analytics off until the trader opts in. */
export const DEFAULT_SETTINGS: TraderSettings = { dataSaver: false, voiceLanguage: 'en', analyticsOptIn: false };

/** Reads settings from untrusted stored data, falling back to defaults field by field. */
export function decodeSettings(data: unknown): TraderSettings {
  const raw = typeof data === 'object' && data !== null ? (data as Record<string, unknown>) : {};
  return {
    dataSaver: typeof raw['dataSaver'] === 'boolean' ? raw['dataSaver'] : DEFAULT_SETTINGS.dataSaver,
    voiceLanguage: raw['voiceLanguage'] === 'pcm_yo' ? 'pcm_yo' : 'en',
    analyticsOptIn: typeof raw['analyticsOptIn'] === 'boolean' ? raw['analyticsOptIn'] : DEFAULT_SETTINGS.analyticsOptIn,
  };
}
