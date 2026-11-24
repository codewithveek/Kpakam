import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { MESSAGES } from '@/shared/messages/en';
import { ChevronRightIcon, MicIcon, SparkIcon } from '@/shared/ui/icons';
import { ListPanel, SectionHeader } from '@/shared/ui/list';
import { PageHeader } from '@/shared/ui/page-header';
import type { TraderSettings } from '../domain/settings';
import { AccountPanel } from './account-panel';
import { LanguageSheet, VOICE_OPTIONS } from './language-sheet';
import { StatsPanel } from './stats-panel';
import { ToggleRow } from './toggle-row';

const S = MESSAGES.settings.sections;
const TILE = 'flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-surface-2 text-text';

/** Settings: data control (F7), voice language (F8), stats (F6), account and privacy. */
export function SettingsPage() {
  const { user, queue } = useServices();
  const settings = useAppStore((s) => s.settings);
  const setSettings = useAppStore((s) => s.setSettings);
  const [languageOpen, setLanguageOpen] = useState(false);
  function save(patch: Partial<TraderSettings>) {
    const next = { ...settings, ...patch };
    setSettings(next);
    void user.saveSettings(next);
    if (patch.dataSaver === false) void queue.drain();
  }
  const language = VOICE_OPTIONS.find((o) => o.value === settings.voiceLanguage)?.label ?? MESSAGES.settings.voiceAuto;
  return (
    <div className="flex flex-col gap-2">
      <PageHeader title={MESSAGES.settings.title} />
      <SectionHeader title={S.voice} />
      <ListPanel>
        <li>
          <button
            type="button"
            onClick={() => setLanguageOpen(true)}
            className="press flex min-h-[72px] w-full items-center gap-3 px-4 text-left"
            data-testid="voice-language"
          >
            <span className={TILE}>
              <MicIcon width={20} height={20} />
            </span>
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="text-[16px] font-semibold">{MESSAGES.settings.voiceLanguage}</span>
              <span className="truncate text-[13px] text-muted">{MESSAGES.settings.voiceHelp[settings.voiceLanguage]}</span>
            </span>
            <span className="shrink-0 rounded-full bg-surface-2 px-3 py-1.5 text-[14px] font-semibold">{language}</span>
            <ChevronRightIcon width={18} height={18} className="shrink-0 text-muted" />
          </button>
        </li>
      </ListPanel>
      <SectionHeader title={S.data} />
      <ListPanel>
        <li>
          <ToggleRow
            label={MESSAGES.settings.dataSaver}
            help={MESSAGES.settings.dataSaverHelp}
            checked={settings.dataSaver}
            onChange={(dataSaver) => save({ dataSaver })}
          />
        </li>
      </ListPanel>
      <SectionHeader title={S.week} />
      <StatsPanel />
      <SectionHeader title={S.reports} />
      <Link to="/insights" className="press flex min-h-[72px] items-center gap-3 rounded-[24px] bg-surface px-4">
        <span className={TILE}>
          <SparkIcon width={20} height={20} />
        </span>
        <span className="flex-1">
          <span className="block text-[16px] font-semibold">{MESSAGES.settings.insights}</span>
          <span className="block text-[13px] text-muted">{MESSAGES.settings.insightsHelp}</span>
        </span>
        <ChevronRightIcon width={18} height={18} className="text-muted" />
      </Link>
      <SectionHeader title={S.privacy} />
      <ListPanel>
        <li>
          <ToggleRow label={MESSAGES.settings.analytics} checked={settings.analyticsOptIn} onChange={(analyticsOptIn) => save({ analyticsOptIn })} />
        </li>
      </ListPanel>
      <SectionHeader title={S.account} />
      <AccountPanel />
      <LanguageSheet
        open={languageOpen}
        value={settings.voiceLanguage}
        onChange={(voiceLanguage) => save({ voiceLanguage })}
        onClose={() => setLanguageOpen(false)}
      />
    </div>
  );
}
