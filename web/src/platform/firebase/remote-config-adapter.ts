import defaults from '@contract/remote-config.defaults.json';
import type { FirebaseApp } from 'firebase/app';

/** Typed view of the shared Remote Config keys (packages/contract/remote-config.defaults.json). */
export interface KpakamConfig {
  readonly cloudModelName: string;
  readonly maxDeviceChars: number;
  readonly languageThreshold: number;
  readonly minBattery: number;
  readonly promptVersionParse: string;
  readonly insightsEnabled: boolean;
  readonly cloudEnabled: boolean;
  readonly cloudRateLimitPerMinute: number;
}

export interface RemoteConfigAdapter {
  /** Current values; bundled defaults until a fetch succeeds, so routing works offline on first launch. */
  values(): KpakamConfig;
  /** Fetches and activates when online. Never throws. */
  refresh(): Promise<void>;
}

/** The bundled defaults as a typed config. */
export const DEFAULT_CONFIG: KpakamConfig = {
  cloudModelName: defaults.cloud_model_name,
  maxDeviceChars: defaults.route_max_device_chars,
  languageThreshold: defaults.route_language_threshold,
  minBattery: defaults.route_min_battery,
  promptVersionParse: defaults.prompt_version_parse,
  insightsEnabled: defaults.insights_enabled,
  cloudEnabled: defaults.cloud_enabled,
  cloudRateLimitPerMinute: defaults.cloud_rate_limit_per_minute,
};

const FETCH_INTERVAL_MS = 60 * 60 * 1000;

/** Remote Config adapter. The SDK loads lazily so it never delays first paint. */
export function createRemoteConfigAdapter(app: FirebaseApp, options: { readonly fetchEnabled: boolean }): RemoteConfigAdapter {
  let current = DEFAULT_CONFIG;
  return {
    values: () => current,
    async refresh() {
      // Emulator and e2e builds have no real project: keep the bundled defaults.
      if (!options.fetchEnabled) return;
      try {
        const rc = await import('firebase/remote-config');
        const remote = rc.getRemoteConfig(app);
        remote.settings.minimumFetchIntervalMillis = FETCH_INTERVAL_MS;
        remote.defaultConfig = defaults;
        await rc.fetchAndActivate(remote);
        current = {
          cloudModelName: rc.getString(remote, 'cloud_model_name'),
          maxDeviceChars: rc.getNumber(remote, 'route_max_device_chars'),
          languageThreshold: rc.getNumber(remote, 'route_language_threshold'),
          minBattery: rc.getNumber(remote, 'route_min_battery'),
          promptVersionParse: rc.getString(remote, 'prompt_version_parse'),
          insightsEnabled: rc.getBoolean(remote, 'insights_enabled'),
          cloudEnabled: rc.getBoolean(remote, 'cloud_enabled'),
          cloudRateLimitPerMinute: rc.getNumber(remote, 'cloud_rate_limit_per_minute'),
        };
      } catch {
        // Offline or blocked: keep the last activated or bundled values.
      }
    },
  };
}
