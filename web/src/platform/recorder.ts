/** A finished audio clip. */
export interface AudioClip {
  readonly blob: Blob;
  readonly mimeType: string;
  readonly durationMs: number;
}

export interface Recording {
  readonly done: Promise<AudioClip>;
  /** The live microphone stream, for the sound wave. */
  readonly stream: MediaStream;
  stop(): void;
}

export type RecorderError = 'MIC_DENIED' | 'UNSUPPORTED';

const PREFERRED_TYPES = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/webm'];
/** PRD F8: clips are capped at 30 s so they stay small on mobile data. */
export const MAX_CLIP_MS = 30_000;
const BITS_PER_SECOND = 24_000;

function pickMimeType(): string | null {
  if (typeof MediaRecorder === 'undefined') return null;
  return PREFERRED_TYPES.find((type) => MediaRecorder.isTypeSupported(type)) ?? null;
}

/**
 * Records Opus audio (WebM in Chrome) for cloud transcription, capped at 30 s
 * (about 90 KB at 24 kbps).
 */
export async function startRecording(): Promise<{ ok: true; recording: Recording } | { ok: false; error: RecorderError }> {
  const mimeType = pickMimeType();
  if (mimeType === null || !('mediaDevices' in navigator)) return { ok: false, error: 'UNSUPPORTED' };
  let stream: MediaStream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, noiseSuppression: true, echoCancellation: true } });
  } catch {
    return { ok: false, error: 'MIC_DENIED' };
  }
  const recorder = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: BITS_PER_SECOND });
  const chunks: Blob[] = [];
  const started = performance.now();
  const timer = setTimeout(() => recorder.state === 'recording' && recorder.stop(), MAX_CLIP_MS);
  const done = new Promise<AudioClip>((resolve) => {
    recorder.ondataavailable = (event) => chunks.push(event.data);
    recorder.onstop = () => {
      clearTimeout(timer);
      stream.getTracks().forEach((track) => track.stop());
      const type = mimeType.split(';')[0] ?? 'audio/webm';
      resolve({ blob: new Blob(chunks, { type }), mimeType: type, durationMs: Math.round(performance.now() - started) });
    };
  });
  recorder.start();
  return { ok: true, recording: { done, stream, stop: () => recorder.state === 'recording' && recorder.stop() } };
}

/** Opens the microphone only to draw the sound wave (phone listening without a recording). */
export async function openMeterStream(): Promise<MediaStream | null> {
  if (!('mediaDevices' in navigator)) return null;
  try {
    return await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1 } });
  } catch {
    return null;
  }
}

/** Releases a microphone stream. */
export function closeStream(stream: MediaStream | null): void {
  stream?.getTracks().forEach((track) => track.stop());
}
