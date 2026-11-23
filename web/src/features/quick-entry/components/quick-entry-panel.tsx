import { useCallback, useId, useRef, useState, type SyntheticEvent } from 'react';
import { useAppStore } from '@/app/app-store';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { CameraIcon, SendIcon } from '@/shared/ui/icons';
import { MAX_NOTE_CHARS, useQuickEntry } from '../hooks/use-quick-entry';
import { useVoiceEntry } from '../hooks/use-voice-entry';
import { MicButton } from './mic-button';

/** F1 + F8: type or speak one sentence; Kpakam turns it into drafts. */
export function QuickEntryPanel() {
  const { busy, submitText, submitPhoto, prepareModel } = useQuickEntry();
  const [text, setText] = useState('');
  const inputId = useId();
  const helpId = useId();
  const fileRef = useRef<HTMLInputElement>(null);
  const progress = useAppStore((s) => s.downloadProgress);
  const onTranscript = useCallback((transcript: string) => setText(transcript.slice(0, MAX_NOTE_CHARS)), []);
  const voice = useVoiceEntry(onTranscript);
  const left = MAX_NOTE_CHARS - text.length;

  async function onSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (await submitText(text)) setText('');
  }

  return (
    <Card aria-labelledby={inputId}>
      <form onSubmit={(e) => void onSubmit(e)} className="flex flex-col gap-3">
        <label htmlFor={inputId} className="text-lg font-bold">
          {MESSAGES.quickEntry.label}
        </label>
        {voice.state.type === 'local' && voice.state.partial !== '' && <p className="text-muted italic">“{voice.state.partial}”</p>}
        <textarea
          id={inputId}
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX_NOTE_CHARS))}
          onFocus={prepareModel}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) void onSubmit(e);
          }}
          rows={3}
          maxLength={MAX_NOTE_CHARS}
          placeholder={MESSAGES.quickEntry.placeholder}
          aria-describedby={helpId}
          className="w-full resize-none rounded-xl border border-border bg-bg p-3 text-lg leading-snug placeholder:text-muted"
        />
        <div className="flex items-center justify-between gap-3">
          <p id={helpId} className="text-sm text-muted">
            {progress !== null ? MESSAGES.inference.downloading(Math.round(progress * 100)) : MESSAGES.quickEntry.charsLeft(left)}
          </p>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file !== undefined) void submitPhoto(file);
              e.target.value = '';
            }}
          />
        </div>
        <div className="flex items-end justify-between gap-3">
          <MicButton state={voice.state} maxSeconds={voice.maxSeconds} onStart={() => void voice.start()} onStop={voice.stop} disabled={busy} />
          <div className="flex gap-2">
            <Button variant="secondary" icon={<CameraIcon />} onClick={() => fileRef.current?.click()} disabled={busy} aria-label={MESSAGES.quickEntry.photo} />
            <Button type="submit" icon={<SendIcon />} disabled={busy || text.trim() === ''}>
              {busy ? MESSAGES.quickEntry.working : MESSAGES.quickEntry.submit}
            </Button>
          </div>
        </div>
      </form>
    </Card>
  );
}
