import { useCallback, useEffect, useRef, useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices, type Services } from '@/app/services';
import { scoreLanguage } from '@/inference/domain/language-score';
import { isMixedSpeech, routeSpeech } from '@/inference/domain/route-decision';
import { closeStream, MAX_CLIP_MS, openMeterStream, startRecording, type AudioClip, type Recording } from '@/platform/recorder';
import type { LocalHearing, LocalTranscription } from '@/platform/speech';
import { MESSAGES } from '@/shared/messages/en';

/** Where the voice screen is. */
export type VoiceState =
  | { readonly type: 'idle' }
  | { readonly type: 'listening'; readonly live: boolean; readonly startedAt: number; readonly partial: string }
  | { readonly type: 'processing' }
  | { readonly type: 'review'; readonly words: string; readonly seconds: number }
  | { readonly type: 'sent'; readonly message: string }
  | { readonly type: 'error'; readonly message: string };

interface Session {
  readonly local: LocalTranscription | null;
  readonly recording: Recording | null;
  readonly meter: MediaStream | null;
}

type SetState = (state: VoiceState) => void;
type SessionRef = { current: Session | null };

function stopSession(session: Session | null): void {
  session?.local?.stop();
  session?.recording?.stop();
  closeStream(session?.meter ?? null);
}

/** Sends a clip to be read online (or later, offline) and returns what to tell the trader. */
async function submitClip(queue: Services['queue'], clip: { audio: AudioClip; mixedHeard: boolean }): Promise<string> {
  const job = await queue.submit({ type: 'audio', blob: clip.audio.blob, mimeType: clip.audio.mimeType, mixedHeard: clip.mixedHeard });
  if (job.state === 'queued') return MESSAGES.voice.queued;
  return clip.mixedHeard ? MESSAGES.voice.mixed : MESSAGES.voice.sent;
}

/** Best-effort parallel recording: if the microphone is busy or refused, the phone's words are used alone. */
async function tryRecording(): Promise<Recording | null> {
  const started = await startRecording();
  return started.ok ? started.recording : null;
}

function heardMixed(heard: LocalHearing, threshold: number): boolean {
  return isMixedSpeech({ transcript: heard.transcript, confidence: heard.confidence, languageScore: scoreLanguage(heard.transcript), threshold });
}

/** Phone listening with live words. "Any" mode also records the clip for the mixed-speech check. */
function useLocalListening(deps: { setState: SetState; sessionRef: SessionRef; setStream: (s: MediaStream | null) => void }) {
  const { speech, queue, remoteConfig } = useServices();
  const voiceLanguage = useAppStore((s) => s.settings.voiceLanguage);
  const { setState, sessionRef, setStream } = deps;
  const finish = useCallback(
    async (heard: LocalHearing, take: { readonly recording: Recording | null; readonly startedAt: number }) => {
      const { recording, startedAt } = take;
      const mixed = voiceLanguage === 'auto' && heardMixed(heard, remoteConfig.values().languageThreshold);
      recording?.stop();
      const audio = recording === null ? null : await recording.done;
      if (mixed && audio !== null) setState({ type: 'sent', message: await submitClip(queue, { audio, mixedHeard: true }) });
      else if (heard.transcript !== '')
        setState({ type: 'review', words: heard.transcript, seconds: Math.max(0, Math.floor((Date.now() - startedAt) / 1000)) });
      else setState({ type: 'error', message: MESSAGES.voice.nothingHeard });
    },
    [voiceLanguage, remoteConfig, queue, setState],
  );
  return useCallback(async () => {
    const startedAt = Date.now();
    const local = speech.startLocal((partial) => setState({ type: 'listening', live: true, startedAt, partial }));
    if (local === null) return false;
    const recording = voiceLanguage === 'auto' ? await tryRecording() : null;
    const meter = recording === null ? await openMeterStream() : null;
    sessionRef.current = { local, recording, meter };
    setStream(recording?.stream ?? meter);
    setState({ type: 'listening', live: true, startedAt, partial: '' });
    void local.done
      .then(async (heard) => {
        setState({ type: 'processing' });
        await finish(heard, { recording, startedAt });
      })
      .catch(() => setState({ type: 'error', message: MESSAGES.voice.unsupported }))
      .finally(() => {
        closeStream(meter);
        setStream(null);
      });
    return true;
  }, [speech, voiceLanguage, finish, setState, sessionRef, setStream]);
}

/** Recording only: the clip is read online (words appear after it is sent), or saved until the network returns. */
function useClipRecording(deps: { setState: SetState; sessionRef: SessionRef; setStream: (s: MediaStream | null) => void }) {
  const { queue } = useServices();
  const { setState, sessionRef, setStream } = deps;
  return useCallback(async () => {
    const started = await startRecording();
    if (!started.ok) {
      setState({ type: 'error', message: started.error === 'MIC_DENIED' ? MESSAGES.voice.micDenied : MESSAGES.voice.unsupported });
      return;
    }
    sessionRef.current = { local: null, recording: started.recording, meter: null };
    setStream(started.recording.stream);
    setState({ type: 'listening', live: false, startedAt: Date.now(), partial: '' });
    const audio = await started.recording.done;
    setStream(null);
    setState({ type: 'processing' });
    setState({ type: 'sent', message: await submitClip(queue, { audio, mixedHeard: false }) });
  }, [queue, setState, sessionRef, setStream]);
}

/**
 * F8 voice entry. By default ("Any" language) the phone listens for free, shows the words live, and
 * records the clip at the same time. If it heard Pidgin or Yoruba, was unsure, or heard nothing, the
 * clip is read online, which understands mixed English, Pidgin and Yoruba. Otherwise the trader checks
 * (and can fix) the words before Kpakam reads them. Without a phone recogniser, the clip is read online.
 */
export function useVoiceEntry() {
  const { speech, network, remoteConfig } = useServices();
  const voiceLanguage = useAppStore((s) => s.settings.voiceLanguage);
  const [state, setState] = useState<VoiceState>({ type: 'idle' });
  const [stream, setStream] = useState<MediaStream | null>(null);
  const session = useRef<Session | null>(null);
  const startLocal = useLocalListening({ setState, sessionRef: session, setStream });
  const startClip = useClipRecording({ setState, sessionRef: session, setStream });

  useEffect(() => () => stopSession(session.current), []);

  const start = useCallback(async () => {
    setState({ type: 'processing' });
    const localSpeech = await speech.localState();
    const decision = routeSpeech({ voiceLanguage, localSpeech, online: network.isOnline(), cloudEnabled: remoteConfig.values().cloudEnabled });
    if (decision.target === 'device' && (await startLocal())) return;
    await startClip();
  }, [speech, voiceLanguage, network, remoteConfig, startLocal, startClip]);

  const finish = useCallback(() => stopSession(session.current), []);
  const reset = useCallback(() => {
    stopSession(session.current);
    session.current = null;
    setState({ type: 'idle' });
  }, []);

  return { state, stream, start, finish, reset, maxSeconds: MAX_CLIP_MS / 1000 };
}
