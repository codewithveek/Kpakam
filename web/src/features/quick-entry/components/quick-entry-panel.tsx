import { useRef, type SyntheticEvent } from 'react';
import { useAppStore } from '@/app/app-store';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { CameraIcon, MicIcon, SendIcon } from '@/shared/ui/icons';
import { MAX_NOTE_CHARS, useQuickEntry } from '../hooks/use-quick-entry';

const SHOW_COUNT_BELOW = 40;

function ExampleChips({ onPick }: { readonly onPick: (text: string) => void }) {
  return (
    <div className="no-scrollbar -mx-1 flex gap-2 overflow-x-auto px-1">
      {MESSAGES.quickEntry.examples.map((example) => (
        <button
          key={example.label}
          type="button"
          onClick={() => onPick(example.text)}
          className="press min-h-10 shrink-0 rounded-full bg-surface-2 px-4 text-[14px] font-medium text-text"
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

const roundTool = 'press flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-text disabled:opacity-40';

/** F1 + F8: type one sentence, or tap the mic to speak it; Kpakam turns it into entries. */
export function QuickEntryPanel() {
  const { busy, submitText, submitPhoto, prepareModel } = useQuickEntry();
  const text = useAppStore((s) => s.noteText);
  const setText = useAppStore((s) => s.setNoteText);
  const openVoice = useAppStore((s) => s.setVoiceOpen);
  const fileRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  async function onSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (await submitText(text)) setText('');
  }
  function pick(example: string) {
    setText((text + example).trimStart().slice(0, MAX_NOTE_CHARS));
    inputRef.current?.focus();
  }

  return (
    <section className="rounded-[28px] bg-surface p-5">
      <form onSubmit={(e) => void onSubmit(e)} className="flex flex-col gap-4">
        <label htmlFor="quick-entry-input" className="sr-only">
          {MESSAGES.quickEntry.label}
        </label>
        <textarea
          id="quick-entry-input"
          ref={inputRef}
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX_NOTE_CHARS))}
          onFocus={prepareModel}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) void onSubmit(e);
          }}
          rows={3}
          maxLength={MAX_NOTE_CHARS}
          placeholder={MESSAGES.quickEntry.placeholder}
          className="w-full resize-none bg-transparent text-[18px] leading-snug tracking-[-0.01em] outline-none placeholder:text-muted"
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
        <div className="flex items-center gap-2">
          <button type="button" className={roundTool} onClick={() => fileRef.current?.click()} disabled={busy} aria-label={MESSAGES.quickEntry.photo}>
            <CameraIcon width={22} height={22} />
          </button>
          <button type="button" className={roundTool} onClick={() => openVoice(true)} disabled={busy} aria-label={MESSAGES.voice.startButton}>
            <MicIcon width={22} height={22} />
          </button>
          <Button
            type="submit"
            className="ml-auto"
            icon={busy ? <span className="spinner h-4 w-4 rounded-full border-2 border-current border-t-transparent" /> : <SendIcon width={18} height={18} />}
            disabled={busy || text.trim() === ''}
          >
            {MESSAGES.quickEntry.submit}
          </Button>
        </div>
      </form>
    </section>
  );
}
