import { Link } from '@tanstack/react-router';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import type { VoiceLanguage } from '@/inference/domain/route-decision';
import { MESSAGES } from '@/shared/messages/en';
import { Card } from '@/shared/ui/card';
import { SparkIcon } from '@/shared/ui/icons';
import type { TraderSettings } from '../domain/settings';
import { AccountPanel } from './account-panel';
import { StatsPanel } from './stats-panel';
import { ToggleRow } from './toggle-row';

const VOICE_OPTIONS: readonly { value: VoiceLanguage; label: string }[] = [
  { value: 'en', label: MESSAGES.settings.voiceEnglish },
  { value: 'pcm_yo', label: MESSAGES.settings.voicePidgin },
];

/** Settings: data control (F7), voice language (F8), stats (F6), account and privacy. */
export function SettingsPage() {
  const { user, queue } = useServices();
  const settings = useAppStore((s) => s.settings);
  const setSettings = useAppStore((s) => s.setSettings);
  function save(patch: Partial<TraderSettings>) {
    const next = { ...settings, ...patch };
    setSettings(next);
    void user.saveSettings(next);
    if (patch.dataSaver === false) void queue.drain();
  }
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-bold">{MESSAGES.settings.title}</h1>
      <Card className="flex flex-col gap-5">
        <ToggleRow
          label={MESSAGES.settings.dataSaver}
          help={MESSAGES.settings.dataSaverHelp}
          checked={settings.dataSaver}
          onChange={(dataSaver) => save({ dataSaver })}
        />
        <fieldset>
          <legend className="font-semibold">{MESSAGES.settings.voiceLanguage}</legend>
          <p className="mb-2 text-sm text-muted">{MESSAGES.settings.voiceHelp}</p>
          <div className="flex flex-wrap gap-2">
            {VOICE_OPTIONS.map((option) => (
              <label
                key={option.value}
                className={`flex min-h-12 cursor-pointer items-center gap-2 rounded-xl border px-4 ${settings.voiceLanguage === option.value ? 'border-primary bg-device-soft' : 'border-border'}`}
              >
                <input
                  type="radio"
                  name="voice-language"
                  value={option.value}
                  checked={settings.voiceLanguage === option.value}
                  onChange={() => save({ voiceLanguage: option.value })}
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>
        <ToggleRow label={MESSAGES.settings.analytics} checked={settings.analyticsOptIn} onChange={(analyticsOptIn) => save({ analyticsOptIn })} />
      </Card>
      <StatsPanel />
      <Link to="/insights" className="flex min-h-14 items-center gap-2 rounded-2xl border border-border bg-surface px-4 font-semibold hover:border-primary">
        <SparkIcon className="text-accent" />
        {MESSAGES.settings.insights}
      </Link>
      <AccountPanel />
    </div>
  );
}
