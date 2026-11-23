import { useCallback, useEffect, useRef, useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { routeSpeech } from '@/inference/domain/route-decision';
import { MAX_CLIP_MS, startRecording, type Recording } from '@/platform/recorder';
import type { LocalTranscription } from '@/platform/speech';
import { MESSAGES } from '@/shared/messages/en';

export type VoiceState =
  | { readonly type: 'idle' }
  | { readonly type: 'local'; readonly partial: string }
  | { readonly type: 'recording'; readonly startedAt: number }
  | { readonly type: 'processing' };

/**
 * F8 voice entry. English with local speech → on-device transcript into the text box
 * (the trader checks it before drafts are made). Otherwise → a ≤30 s Opus clip sent
 * to cloud Gemini, or queued when offline.
 */
export function useVoiceEntry(onTranscript: (text: string) => void) {
  const { speech, network, queue, remoteConfig } = useServices();
  const voiceLanguage = useAppStore((s) => s.settings.voiceLanguage);
  const showToast = useAppStore((s) => s.showToast);
  const [state, setState] = useState<VoiceState>({ type: 'idle' });
  const session = useRef<LocalTranscription | Recording | null>(null);

  useEffect(() => () => session.current?.stop(), []);

  const startLocal = useCallback(() => {
    const local = speech.startLocal((partial) => setState({ type: 'local', partial }));
    if (local === null) return false;
    session.current = local;
    setState({ type: 'local', partial: '' });
    void local.done
      .then((text) => text !== '' && onTranscript(text))
      .catch(() => showToast(MESSAGES.voice.unsupported))
      .finally(() => setState({ type: 'idle' }));
    return true;
  }, [speech, onTranscript, showToast]);

  const startClip = useCallback(async () => {
    const started = await startRecording();
    if (!started.ok) {
      showToast(started.error === 'MIC_DENIED' ? MESSAGES.voice.micDenied : MESSAGES.voice.unsupported);
      return;
    }
    session.current = started.recording;
    setState({ type: 'recording', startedAt: Date.now() });
    const clip = await started.recording.done;
    setState({ type: 'processing' });
    const job = await queue.submit({ type: 'audio', blob: clip.blob, mimeType: clip.mimeType });
    if (job.state === 'queued') showToast(MESSAGES.voice.queued);
    setState({ type: 'idle' });
  }, [queue, showToast]);

  const start = useCallback(async () => {
    const localSpeech = await speech.localState();
    const decision = routeSpeech({ voiceLanguage, localSpeech, online: network.isOnline(), cloudEnabled: remoteConfig.values().cloudEnabled });
    if (decision.target === 'device' && startLocal()) return;
    await startClip();
  }, [speech, voiceLanguage, network, remoteConfig, startLocal, startClip]);

  return { state, start, stop: useCallback(() => session.current?.stop(), []), maxSeconds: MAX_CLIP_MS / 1000 };
}
