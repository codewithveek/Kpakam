import { useEffect, useState } from 'react';
import { MESSAGES } from '@/shared/messages/en';
import { MicIcon, StopIcon } from '@/shared/ui/icons';
import type { VoiceState } from '../hooks/use-voice-entry';

export interface MicButtonProps {
  readonly state: VoiceState;
  readonly maxSeconds: number;
  readonly onStart: () => void;
  readonly onStop: () => void;
  readonly disabled?: boolean;
}

function useSecondsLeft(state: VoiceState, maxSeconds: number): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (state.type !== 'recording') return undefined;
    const timer = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(timer);
  }, [state.type]);
  if (state.type !== 'recording') return null;
  return Math.max(0, maxSeconds - Math.floor((now - state.startedAt) / 1000));
}

function caption(state: VoiceState, secondsLeft: number | null): string {
  if (secondsLeft !== null) return MESSAGES.voice.seconds(secondsLeft);
  if (state.type === 'local') return MESSAGES.voice.listening;
  return MESSAGES.voice.start;
}

/** F8: a large mic button. Tap to start, tap again to stop (works with keyboard and screen readers). */
export function MicButton({ state, maxSeconds, onStart, onStop, disabled = false }: MicButtonProps) {
  const active = state.type === 'local' || state.type === 'recording';
  const secondsLeft = useSecondsLeft(state, maxSeconds);
  return (
    <div className="flex min-w-0 items-center gap-3">
      <button
        type="button"
        onClick={active ? onStop : onStart}
        disabled={disabled || state.type === 'processing'}
        aria-pressed={active}
        aria-label={active ? MESSAGES.voice.stopButton : MESSAGES.voice.startButton}
        className={`press relative flex h-14 w-14 shrink-0 items-center justify-center rounded-full disabled:opacity-40 ${active ? 'bg-danger text-white' : 'bg-device-soft text-device'}`}
      >
        {active && <span aria-hidden="true" className="absolute inset-0 animate-ping rounded-full bg-danger/30 motion-reduce:hidden" />}
        {active ? <StopIcon width={24} height={24} /> : <MicIcon width={26} height={26} />}
      </button>
      <span className="truncate text-[13px] font-medium text-muted" aria-live="polite">
        {caption(state, secondsLeft)}
      </span>
    </div>
  );
}
