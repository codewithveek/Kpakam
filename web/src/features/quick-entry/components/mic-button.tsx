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

/** F8: a large mic button. Tap to start, tap again to stop (works with keyboard and screen readers). */
export function MicButton({ state, maxSeconds, onStart, onStop, disabled = false }: MicButtonProps) {
  const active = state.type === 'local' || state.type === 'recording';
  const secondsLeft = useSecondsLeft(state, maxSeconds);
  return (
    <div className="flex flex-col items-center gap-1">
      <button
        type="button"
        onClick={active ? onStop : onStart}
        disabled={disabled || state.type === 'processing'}
        aria-pressed={active}
        aria-label={active ? MESSAGES.voice.stopButton : MESSAGES.voice.startButton}
        className={`flex h-16 w-16 items-center justify-center rounded-full shadow-sm transition-colors disabled:opacity-50 ${active ? 'bg-danger text-white' : 'bg-primary text-on-primary hover:bg-primary-strong'}`}
      >
        {active ? <StopIcon width={28} height={28} /> : <MicIcon width={28} height={28} />}
      </button>
      <span className="text-xs text-muted" aria-live="polite">
        {secondsLeft !== null ? MESSAGES.voice.seconds(secondsLeft) : state.type === 'local' ? MESSAGES.voice.listening : MESSAGES.voice.start}
      </span>
    </div>
  );
}
