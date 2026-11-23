import type { LocalSpeechState } from '@/inference/domain/route-decision';

/** Minimal typing of Chrome's Web Speech API with on-device recognition (Chrome 139+). */
interface SpeechRecognitionResultLike {
  readonly isFinal: boolean;
  readonly 0: { readonly transcript: string };
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

/** A live on-device transcription session. */
export interface LocalTranscription {
  /** Resolves with the final transcript when stopped. */
  readonly done: Promise<string>;
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
      let transcript = '';
      const done = new Promise<string>((resolve, reject) => {
        recognition.onresult = (event) => {
          transcript = joinResults(event.results);
          onPartial(transcript);
        };
        recognition.onerror = (event) => (event.error === 'no-speech' ? resolve('') : reject(new Error(event.error)));
        recognition.onend = () => resolve(transcript);
      });
      recognition.start();
      return { done, stop: () => recognition.stop() };
    },
  };
}
