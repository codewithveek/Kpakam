import { useNavigate } from '@tanstack/react-router';
import { useEffect, useRef, useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { MESSAGES } from '@/shared/messages/en';
import { Button, IconButton } from '@/shared/ui/button';
import { CloseIcon, MicIcon, SendIcon, SparkIcon, StopIcon } from '@/shared/ui/icons';
import { Soundwave } from '@/shared/ui/soundwave';
import { MAX_NOTE_CHARS, useQuickEntry } from '../hooks/use-quick-entry';
import { useVoiceEntry, type VoiceState } from '../hooks/use-voice-entry';

const AUTO_CLOSE_MS = 1800;
const V = MESSAGES.voice;

function useElapsed(startedAt: number | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (startedAt === null) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(timer);
  }, [startedAt]);
  return startedAt === null ? 0 : Math.max(0, Math.floor((now - startedAt) / 1000));
}

const clock = (seconds: number) => `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

/** Top card: status, the live sound wave, and a big timer. */
function RecorderCard({ state, stream, maxSeconds }: { readonly state: VoiceState; readonly stream: MediaStream | null; readonly maxSeconds: number }) {
  const listening = state.type === 'listening';
  const elapsed = useElapsed(listening ? state.startedAt : null);
  return (
    <section className="flex flex-col items-center gap-4 rounded-[28px] bg-surface px-5 pb-6 pt-5">
      <p
        className={`inline-flex min-h-8 items-center gap-2 rounded-full px-3 text-[13px] font-semibold ${listening ? 'bg-danger-soft text-danger' : 'bg-surface-2 text-muted'}`}
      >
        <span aria-hidden="true" className={`h-2 w-2 rounded-full ${listening ? 'animate-pulse bg-live motion-reduce:animate-none' : 'bg-muted'}`} />
        {listening ? V.listeningStatus : state.type === 'review' ? V.heardStatus : state.type === 'processing' ? V.preparing : V.title}
      </p>
      <Soundwave stream={stream} active={listening} className="w-full" />
      <p className="amount display text-[52px] font-semibold" aria-live="off">
        {clock(state.type === 'review' ? state.seconds : elapsed)}
      </p>
      <p className="text-[13px] font-medium text-muted" aria-live="polite">
        {listening ? V.secondsLeft(Math.max(0, maxSeconds - elapsed)) : ' '}
      </p>
    </section>
  );
}

function WordsBody({ state, words, setWords }: { readonly state: VoiceState; readonly words: string; readonly setWords: (w: string) => void }) {
  if (state.type === 'review') {
    return (
      <>
        <label htmlFor="voice-words" className="sr-only">
          {V.liveWords}
        </label>
        <textarea
          id="voice-words"
          value={words}
          maxLength={MAX_NOTE_CHARS}
          onChange={(e) => setWords(e.target.value)}
          rows={4}
          className="-mx-2 w-[calc(100%+1rem)] flex-1 resize-none rounded-[18px] bg-surface-2 px-3 py-2 text-[24px] font-semibold leading-snug tracking-[-0.02em] outline-none"
        />
        <p className="text-[13px] text-muted">{V.reviewHint}</p>
      </>
    );
  }
  if (state.type === 'listening') {
    if (!state.live) return <p className="text-[17px] text-muted">{V.wordsLater}</p>;
    return state.partial === '' ? (
      <p className="text-[17px] text-muted">{V.liveHint}</p>
    ) : (
      <p className="text-[24px] font-semibold leading-snug tracking-[-0.02em]" aria-live="polite">
        {state.partial}
      </p>
    );
  }
  if (state.type === 'sent' || state.type === 'error')
    return (
      <p role={state.type === 'error' ? 'alert' : 'status'} className="text-[20px] font-semibold leading-snug">
        {state.message}
      </p>
    );
  return (
    <p className="flex items-center gap-3 text-[17px] text-muted">
      <span aria-hidden="true" className="spinner h-5 w-5 rounded-full border-2 border-text border-t-transparent" />
      {MESSAGES.quickEntry.working}
    </p>
  );
}

/** Bottom card: the words, live while speaking and editable afterwards (corrections by hand or by voice). */
function WordsCard(props: { readonly state: VoiceState; readonly words: string; readonly setWords: (w: string) => void }) {
  return (
    <section className="flex min-h-48 flex-1 flex-col gap-3 rounded-[28px] bg-surface p-5">
      <p className="inline-flex items-center gap-1.5 text-[12px] font-semibold uppercase tracking-[0.08em] text-muted">
        <SparkIcon width={14} height={14} />
        {V.liveWords}
      </p>
      <WordsBody {...props} />
    </section>
  );
}

interface ActionsProps {
  readonly state: VoiceState;
  readonly words: string;
  readonly onFinish: () => void;
  readonly onAgain: () => void;
  readonly onRead: () => void;
  readonly onClose: () => void;
}

function Actions({ state, words, onFinish, onAgain, onRead, onClose }: ActionsProps) {
  if (state.type === 'listening' || state.type === 'processing') {
    return (
      <div className="grid grid-cols-2 gap-3">
        <Button variant="secondary" size="lg" onClick={onClose}>
          {V.cancel}
        </Button>
        <Button size="lg" icon={<StopIcon width={18} height={18} />} onClick={onFinish} disabled={state.type !== 'listening'}>
          {V.done}
        </Button>
      </div>
    );
  }
  if (state.type === 'review') {
    return (
      <div className="grid grid-cols-2 gap-3">
        <Button variant="secondary" size="lg" icon={<MicIcon width={18} height={18} />} onClick={onAgain}>
          {V.sayAgain}
        </Button>
        <Button size="lg" icon={<SendIcon width={18} height={18} />} onClick={onRead} disabled={words.trim() === ''}>
          {MESSAGES.quickEntry.submit}
        </Button>
      </div>
    );
  }
  return (
    <div className="grid grid-cols-2 gap-3">
      <Button variant="secondary" size="lg" icon={<MicIcon width={18} height={18} />} onClick={onAgain}>
        {V.sayAgain}
      </Button>
      <Button size="lg" onClick={onClose}>
        {V.close}
      </Button>
    </div>
  );
}

/** Starts listening once, and closes itself shortly after a voice note was sent. */
function useVoiceLifecycle(voice: ReturnType<typeof useVoiceEntry>, close: () => void) {
  const started = useRef(false);
  const { start, state } = voice;
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void start();
  }, [start]);
  useEffect(() => {
    if (state.type !== 'sent') return undefined;
    const timer = setTimeout(close, AUTO_CLOSE_MS);
    return () => clearTimeout(timer);
  }, [state.type, close]);
}

/** F8: the full-screen voice screen (live words, sound wave, timer, then check and read). */
export function VoiceScreen() {
  const voice = useVoiceEntry();
  const { submitText } = useQuickEntry();
  const setOpen = useAppStore((s) => s.setVoiceOpen);
  const navigate = useNavigate();
  const [edited, setEdited] = useState<string | null>(null);
  const words = edited ?? (voice.state.type === 'review' ? voice.state.words : '');
  const dialog = useRef<HTMLDialogElement>(null);
  const close = () => {
    voice.reset();
    setOpen(false);
  };
  useVoiceLifecycle(voice, close);
  useEffect(() => {
    if (dialog.current?.open === false) dialog.current.showModal();
  }, []);
  async function read() {
    close();
    await navigate({ to: '/' });
    await submitText(words);
  }
  const again = () => {
    setEdited(null);
    voice.reset();
    void voice.start();
  };
  return (
    <dialog
      ref={dialog}
      aria-label={V.title}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
      className="m-0 h-dvh max-h-none w-full max-w-none bg-bg p-0 text-text backdrop:bg-bg"
    >
      <div className="sheet-enter mx-auto flex h-full max-w-2xl flex-col gap-3 px-4 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-[calc(0.75rem+env(safe-area-inset-top))]">
        <div className="flex items-center justify-between">
          <IconButton label={V.close} onClick={close}>
            <CloseIcon width={22} height={22} />
          </IconButton>
          <h2 className="text-[17px] font-semibold">{V.title}</h2>
          <span className="w-12" />
        </div>
        <RecorderCard state={voice.state} stream={voice.stream} maxSeconds={voice.maxSeconds} />
        <WordsCard state={voice.state} words={words} setWords={setEdited} />
        <Actions state={voice.state} words={words} onFinish={voice.finish} onAgain={again} onRead={() => void read()} onClose={close} />
      </div>
    </dialog>
  );
}
