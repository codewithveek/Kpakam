import type { LocalSpeechState } from '@/inference/domain/route-decision';

/** Minimal typing of Chrome's Web Speech API with on-device recognition (Chrome 139+). */
interface SpeechRecognitionResultLike {
  readonly isFinal: boolean;
  readonly 0: { readonly transcript: string; readonly confidence?: number };
}
interface SpeechRecognitionEventLike {
  readonly results: ArrayLike<SpeechRecognitionResultLike>;
}
interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  processLocally: boolean;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}
interface SpeechRecognitionStatic {
  new (): SpeechRecognitionLike;
  available?(options: { langs: string[]; processLocally: boolean }): Promise<string>;
  install?(options: { langs: string[]; processLocally: boolean }): Promise<boolean>;
}

const LOCAL = { langs: ['en-US'], processLocally: true };

function recognitionClass(): SpeechRecognitionStatic | null {
  const ctor: unknown = Reflect.get(globalThis, 'SpeechRecognition') ?? Reflect.get(globalThis, 'webkitSpeechRecognition');
  return typeof ctor === 'function' ? (ctor as SpeechRecognitionStatic) : null;
}

/** What the phone heard: the words, and how sure it was (null when the browser does not say). */
export interface LocalHearing {
  readonly transcript: string;
  readonly confidence: number | null;
}

/** A live on-device transcription session. */
export interface LocalTranscription {
  /** Resolves with what was heard when stopped. */
  readonly done: Promise<LocalHearing>;
  stop(): void;
}

/** On-device English speech recognition (F8, `SPEECH_DEVICE`). */
export interface SpeechAdapter {
  /** Asks Chrome for the local model state. Call only when voice is about to be used. */
  localState(): Promise<LocalSpeechState>;
  /** Last known state, without asking Chrome again. */
  cachedState(): LocalSpeechState;
  /** Installs the local English model once, at idle time. */
  installLocal(): Promise<void>;
  startLocal(onPartial: (text: string) => void): LocalTranscription | null;
}

function joinResults(results: ArrayLike<SpeechRecognitionResultLike>): string {
  return Array.from(results, (result) => result[0].transcript)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Average confidence of the results; null when the browser reports none (0 means "not given"). */
function averageConfidence(results: ArrayLike<SpeechRecognitionResultLike>): number | null {
  const values = Array.from(results, (result) => result[0].confidence ?? 0).filter((c) => c > 0);
  return values.length === 0 ? null : values.reduce((a, b) => a + b, 0) / values.length;
}

/**
 * Automated browsers (Playwright, CI) report `navigator.webdriver`. Some headless Chromium
 * builds crash the renderer on `SpeechRecognition.available()`, so we never probe there.
 */
function isAutomated(): boolean {
  return typeof navigator !== 'undefined' && navigator.webdriver;
}

async function probeLocal(): Promise<LocalSpeechState> {
  const ctor = recognitionClass();
  if (ctor?.available === undefined || isAutomated()) return 'unavailable';
  try {
    const state = await ctor.available(LOCAL);
    return state === 'available' || state === 'downloadable' || state === 'downloading' ? state : 'unavailable';
  } catch {
    return 'unavailable';
  }
}

/** Creates the speech adapter over `SpeechRecognition` with `processLocally`. */
export function createSpeechAdapter(): SpeechAdapter {
  let installing: Promise<void> | null = null;
  let cached: LocalSpeechState = 'unavailable';
  return {
    async localState() {
      cached = await probeLocal();
      return cached;
    },
    cachedState: () => cached,
    installLocal() {
      const ctor = recognitionClass();
      if (isAutomated()) return Promise.resolve();
      installing ??= (ctor?.install?.(LOCAL) ?? Promise.resolve(false)).then(
        () => undefined,
        () => undefined,
      );
      return installing;
    },
    startLocal(onPartial) {
      const ctor = recognitionClass();
      if (ctor === null) return null;
      const recognition = new ctor();
      Object.assign(recognition, { lang: 'en-US', interimResults: true, continuous: true, processLocally: true });
      let heard: LocalHearing = { transcript: '', confidence: null };
      const done = new Promise<LocalHearing>((resolve, reject) => {
        recognition.onresult = (event) => {
          heard = { transcript: joinResults(event.results), confidence: averageConfidence(event.results) };
          onPartial(heard.transcript);
        };
        recognition.onerror = (event) => (event.error === 'no-speech' ? resolve(heard) : reject(new Error(event.error)));
        recognition.onend = () => resolve(heard);
      });
      recognition.start();
      return { done, stop: () => recognition.stop() };
    },
  };
}
