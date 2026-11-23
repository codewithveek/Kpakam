import { useCallback, useEffect, useRef, useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices, type Services } from '@/app/services';
import { scoreLanguage } from '@/inference/domain/language-score';
import { isMixedSpeech, routeSpeech } from '@/inference/domain/route-decision';
import { MAX_CLIP_MS, startRecording, type AudioClip, type Recording } from '@/platform/recorder';
import type { LocalHearing, LocalTranscription } from '@/platform/speech';
import { MESSAGES } from '@/shared/messages/en';

export type VoiceState =
  | { readonly type: 'idle' }
  | { readonly type: 'local'; readonly partial: string }
  | { readonly type: 'recording'; readonly startedAt: number }
  | { readonly type: 'processing' };

/** Everything that is listening right now: the phone's recogniser and/or a recording. */
interface Session {
  readonly local: LocalTranscription | null;
  readonly recording: Recording | null;
}

function stopSession(session: Session | null): void {
  session?.local?.stop();
  session?.recording?.stop();
}

/** Sends a clip to be read (online, or later when offline) and tells the trader what happens next. */
async function submitClip(deps: { queue: Services['queue']; showToast: (text: string) => void }, clip: { audio: AudioClip; mixedHeard: boolean }) {
  const job = await deps.queue.submit({ type: 'audio', blob: clip.audio.blob, mimeType: clip.audio.mimeType, mixedHeard: clip.mixedHeard });
  if (job.state === 'queued') deps.showToast(MESSAGES.voice.queued);
  else if (clip.mixedHeard) deps.showToast(MESSAGES.voice.mixed);
}

/** Best-effort parallel recording: if the microphone is busy or refused, the phone's words are used alone. */
async function tryRecording(): Promise<Recording | null> {
  const started = await startRecording();
  return started.ok ? started.recording : null;
}

/** The mixed-speech check from routing.md, with the Remote Config threshold. */
function heardMixed(heard: LocalHearing, threshold: number): boolean {
  return isMixedSpeech({ transcript: heard.transcript, confidence: heard.confidence, languageScore: scoreLanguage(heard.transcript), threshold });
}

type SetState = (state: VoiceState) => void;
type SessionRef = { current: Session | null };

/** Phone listening ("Any" mode also records the clip in parallel for the mixed-speech check). */
function useLocalListening(deps: { setState: SetState; sessionRef: SessionRef; onTranscript: (text: string) => void }) {
  const { speech, queue, remoteConfig } = useServices();
  const voiceLanguage = useAppStore((s) => s.settings.voiceLanguage);
  const showToast = useAppStore((s) => s.showToast);
  const { setState, sessionRef, onTranscript } = deps;
  const finish = useCallback(
    async (heard: LocalHearing, recording: Recording | null) => {
      const mixed = voiceLanguage === 'auto' && heardMixed(heard, remoteConfig.values().languageThreshold);
      recording?.stop();
      const audio = recording === null ? null : await recording.done;
      if (mixed && audio !== null) await submitClip({ queue, showToast }, { audio, mixedHeard: true });
      else if (heard.transcript !== '') onTranscript(heard.transcript);
    },
    [voiceLanguage, remoteConfig, queue, showToast, onTranscript],
  );
  return useCallback(async () => {
    const local = speech.startLocal((partial) => setState({ type: 'local', partial }));
    if (local === null) return false;
    const recording = voiceLanguage === 'auto' ? await tryRecording() : null;
    sessionRef.current = { local, recording };
    setState({ type: 'local', partial: '' });
    void local.done
      .then(async (heard) => {
        setState({ type: 'processing' });
        await finish(heard, recording);
      })
      .catch(() => showToast(MESSAGES.voice.unsupported))
      .finally(() => setState({ type: 'idle' }));
    return true;
  }, [speech, voiceLanguage, finish, showToast, setState, sessionRef]);
}

/** Recording only: the clip is read online, or saved until the network returns. */
function useClipRecording(deps: { setState: SetState; sessionRef: SessionRef }) {
  const { queue } = useServices();
  const showToast = useAppStore((s) => s.showToast);
  const { setState, sessionRef } = deps;
  return useCallback(async () => {
    const started = await startRecording();
    if (!started.ok) {
      showToast(started.error === 'MIC_DENIED' ? MESSAGES.voice.micDenied : MESSAGES.voice.unsupported);
      return;
    }
    sessionRef.current = { local: null, recording: started.recording };
    setState({ type: 'recording', startedAt: Date.now() });
    const audio = await started.recording.done;
    setState({ type: 'processing' });
    await submitClip({ queue, showToast }, { audio, mixedHeard: false });
    setState({ type: 'idle' });
  }, [queue, showToast, setState, sessionRef]);
}

/**
 * F8 voice entry. By default ("Any" language) the phone listens for free and, at the same time,
 * records the clip. If it heard Pidgin or Yoruba, was unsure, or heard nothing, the clip is read
 * online, which understands mixed English, Pidgin and Yoruba. Otherwise the phone's words go into
 * the text box for the trader to check. Without a phone recogniser, the clip is read online (or
 * saved until the network returns).
 */
export function useVoiceEntry(onTranscript: (text: string) => void) {
  const { speech, network, remoteConfig } = useServices();
  const voiceLanguage = useAppStore((s) => s.settings.voiceLanguage);
  const [state, setState] = useState<VoiceState>({ type: 'idle' });
  const session = useRef<Session | null>(null);
  const startLocal = useLocalListening({ setState, sessionRef: session, onTranscript });
  const startClip = useClipRecording({ setState, sessionRef: session });

  useEffect(() => () => stopSession(session.current), []);

  const start = useCallback(async () => {
    const localSpeech = await speech.localState();
    const decision = routeSpeech({ voiceLanguage, localSpeech, online: network.isOnline(), cloudEnabled: remoteConfig.values().cloudEnabled });
    if (decision.target === 'device' && (await startLocal())) return;
    await startClip();
  }, [speech, voiceLanguage, network, remoteConfig, startLocal, startClip]);

  return { state, start, stop: useCallback(() => stopSession(session.current), []), maxSeconds: MAX_CLIP_MS / 1000 };
}
