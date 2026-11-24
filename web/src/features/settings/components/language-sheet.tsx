import type { VoiceLanguage } from '@/inference/domain/route-decision';
import { MESSAGES } from '@/shared/messages/en';
import { CheckIcon } from '@/shared/ui/icons';
import { Sheet } from '@/shared/ui/sheet';

export const VOICE_OPTIONS: readonly { value: VoiceLanguage; label: string }[] = [
  { value: 'auto', label: MESSAGES.settings.voiceAuto },
  { value: 'en', label: MESSAGES.settings.voiceEnglish },
  { value: 'pcm_yo', label: MESSAGES.settings.voicePidgin },
];

export interface LanguageSheetProps {
  readonly open: boolean;
  readonly value: VoiceLanguage;
  readonly onChange: (value: VoiceLanguage) => void;
  readonly onClose: () => void;
}

/** "Language you speak" as a bottom sheet: one row per choice, the chosen one is a black pill with a tick. */
export function LanguageSheet({ open, value, onChange, onClose }: LanguageSheetProps) {
  return (
    <Sheet open={open} title={MESSAGES.settings.voiceLanguage} onClose={onClose}>
      <fieldset className="flex min-w-0 flex-col gap-2">
        <legend className="sr-only">{MESSAGES.settings.voiceLanguage}</legend>
        {VOICE_OPTIONS.map((option) => {
          const active = option.value === value;
          return (
            <label
              key={option.value}
              className={`press flex min-h-[72px] cursor-pointer items-center gap-3 rounded-[22px] px-5 py-4 has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-[var(--color-focus)] ${active ? 'bg-ink text-on-ink' : 'bg-surface-2 text-text'}`}
            >
              <input
                type="radio"
                name="voice-language"
                className="sr-only"
                checked={active}
                onChange={() => {
                  onChange(option.value);
                  onClose();
                }}
              />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="text-[17px] font-semibold">{option.label}</span>
                <span className={`text-[13px] leading-snug ${active ? 'text-on-ink-muted' : 'text-muted'}`}>{MESSAGES.settings.voiceHelp[option.value]}</span>
              </span>
              {active && <CheckIcon width={20} height={20} aria-hidden="true" />}
            </label>
          );
        })}
      </fieldset>
    </Sheet>
  );
}
