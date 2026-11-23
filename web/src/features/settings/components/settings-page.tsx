import { Link } from '@tanstack/react-router';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import type { VoiceLanguage } from '@/inference/domain/route-decision';
import { MESSAGES } from '@/shared/messages/en';
import { ChevronRightIcon, SparkIcon } from '@/shared/ui/icons';
import { SectionHeader } from '@/shared/ui/list';
import { PageHeader } from '@/shared/ui/page-header';
import { Segmented } from '@/shared/ui/segmented';
import type { TraderSettings } from '../domain/settings';
import { AccountPanel } from './account-panel';
import { StatsPanel } from './stats-panel';
import { ToggleRow } from './toggle-row';

const VOICE_OPTIONS: readonly { value: VoiceLanguage; label: string }[] = [
  { value: 'en', label: MESSAGES.settings.voiceEnglish },
  { value: 'pcm_yo', label: MESSAGES.settings.voicePidgin },
];
const GROUP = 'overflow-hidden rounded-[20px] border border-border bg-surface shadow-card';
const S = MESSAGES.settings.sections;

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
    <div className="flex flex-col gap-2">
      <PageHeader title={MESSAGES.settings.title} />
      <SectionHeader title={S.data} />
      <div className={GROUP}>
        <ToggleRow
          label={MESSAGES.settings.dataSaver}
          help={MESSAGES.settings.dataSaverHelp}
          checked={settings.dataSaver}
          onChange={(dataSaver) => save({ dataSaver })}
        />
      </div>
      <SectionHeader title={S.voice} />
      <div className={`${GROUP} flex flex-col gap-3 p-4`}>
        <Segmented
          label={MESSAGES.settings.voiceLanguage}
          value={settings.voiceLanguage}
          options={VOICE_OPTIONS}
          onChange={(voiceLanguage) => save({ voiceLanguage })}
        />
        <p className="text-[13px] leading-snug text-muted">{MESSAGES.settings.voiceHelp}</p>
      </div>
      <SectionHeader title={S.week} />
      <StatsPanel />
      <SectionHeader title={S.reports} />
      <Link to="/insights" className={`${GROUP} press flex min-h-16 items-center gap-3 px-4`}>
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-accent-soft text-accent">
          <SparkIcon width={18} height={18} />
        </span>
        <span className="flex-1">
          <span className="block text-[15px] font-semibold">{MESSAGES.settings.insights}</span>
          <span className="block text-[13px] text-muted">{MESSAGES.settings.insightsHelp}</span>
        </span>
        <ChevronRightIcon width={18} height={18} className="text-muted" />
      </Link>
      <SectionHeader title={S.privacy} />
      <div className={GROUP}>
        <ToggleRow label={MESSAGES.settings.analytics} checked={settings.analyticsOptIn} onChange={(analyticsOptIn) => save({ analyticsOptIn })} />
      </div>
      <SectionHeader title={S.account} />
      <AccountPanel />
    </div>
  );
}
