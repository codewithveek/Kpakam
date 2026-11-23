import { useCallback, useRef, useState, type SyntheticEvent } from 'react';
import { useAppStore } from '@/app/app-store';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { CameraIcon, SendIcon } from '@/shared/ui/icons';
import { MAX_NOTE_CHARS, useQuickEntry } from '../hooks/use-quick-entry';
import { useVoiceEntry } from '../hooks/use-voice-entry';
import { MicButton } from './mic-button';

const SHOW_COUNT_BELOW = 40;

function ExampleChips({ onPick }: { readonly onPick: (text: string) => void }) {
  return (
    <div className="no-scrollbar -mx-1 flex gap-2 overflow-x-auto px-1">
      {MESSAGES.quickEntry.examples.map((example) => (
        <button
          key={example.label}
          type="button"
          onClick={() => onPick(example.text)}
          className="press min-h-10 shrink-0 rounded-full bg-surface-2 px-4 text-sm font-semibold text-text"
        >
          {example.label}
        </button>
      ))}
    </div>
  );
}

function Hint({ left }: { readonly left: number }) {
  const progress = useAppStore((s) => s.downloadProgress);
  if (progress !== null) return <p className="text-[13px] text-muted">{MESSAGES.inference.downloading(Math.round(progress * 100))}</p>;
  if (left > SHOW_COUNT_BELOW) return null;
  return <p className={`text-[13px] ${left < 10 ? 'font-semibold text-accent' : 'text-muted'}`}>{MESSAGES.quickEntry.charsLeft(left)}</p>;
}

/** F1 + F8: type or speak one sentence; Kpakam turns it into drafts. */
export function QuickEntryPanel() {
  const { busy, submitText, submitPhoto, prepareModel } = useQuickEntry();
  const [text, setText] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const onTranscript = useCallback((transcript: string) => setText(transcript.slice(0, MAX_NOTE_CHARS)), []);
  const voice = useVoiceEntry(onTranscript);

  async function onSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (await submitText(text)) setText('');
  }
  function pick(example: string) {
    setText((current) => (current + example).trimStart().slice(0, MAX_NOTE_CHARS));
    inputRef.current?.focus();
  }

  return (
    <section className="rounded-[24px] border border-border bg-surface p-4 shadow-card">
      <form onSubmit={(e) => void onSubmit(e)} className="flex flex-col gap-3">
        <label id="quick-entry-label" htmlFor="quick-entry-input" className="text-[17px] font-semibold">
          {MESSAGES.quickEntry.label}
        </label>
        {voice.state.type === 'local' && voice.state.partial !== '' && <p className="text-muted italic">“{voice.state.partial}”</p>}
        <textarea
          id="quick-entry-input"
          ref={inputRef}
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX_NOTE_CHARS))}
          onFocus={prepareModel}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) void onSubmit(e);
          }}
          rows={2}
          maxLength={MAX_NOTE_CHARS}
          placeholder={MESSAGES.quickEntry.placeholder}
          className="w-full resize-none bg-transparent text-[17px] leading-snug outline-none placeholder:text-muted/80"
        />
        {text === '' ? <ExampleChips onPick={pick} /> : <Hint left={MAX_NOTE_CHARS - text.length} />}
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
        <div className="flex items-center justify-between gap-2 border-t border-border pt-3">
          <MicButton state={voice.state} maxSeconds={voice.maxSeconds} onStart={() => void voice.start()} onStop={voice.stop} disabled={busy} />
          <div className="flex shrink-0 items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              icon={<CameraIcon />}
              onClick={() => fileRef.current?.click()}
              disabled={busy}
              aria-label={MESSAGES.quickEntry.photo}
            />
            <Button
              type="submit"
              icon={busy ? <span className="spinner h-4 w-4 rounded-full border-2 border-current border-t-transparent" /> : <SendIcon width={18} height={18} />}
              disabled={busy || text.trim() === ''}
            >
              {MESSAGES.quickEntry.submit}
            </Button>
          </div>
        </div>
      </form>
    </section>
  );
}
