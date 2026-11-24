import { useCallback, useRef, useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { compressImage } from '@/platform/image';
import { MESSAGES } from '@/shared/messages/en';

export const MAX_NOTE_CHARS = 280;

/** F1 quick entry: submit a typed note or a receipt photo to the job queue. */
export function useQuickEntry() {
  const { queue, inference } = useServices();
  const showToast = useAppStore((s) => s.showToast);
  const setProgress = useAppStore((s) => s.setDownloadProgress);
  const [busy, setBusy] = useState(false);
  const prepared = useRef(false);

  /** Runs on first focus (a user gesture), so Chrome allows the model download if needed. */
  const prepareModel = useCallback(() => {
    if (prepared.current) return;
    prepared.current = true;
    void inference.prepareDeviceModel((fraction) => setProgress(fraction >= 1 ? null : fraction)).finally(() => setProgress(null));
  }, [inference, setProgress]);

  const submitText = useCallback(
    async (text: string): Promise<boolean> => {
      const note = text.trim();
      if (note === '' || note.length > MAX_NOTE_CHARS) return false;
      setBusy(true);
      try {
        const job = await queue.submit({ type: 'text', text: note });
        if (job.state === 'queued') showToast(MESSAGES.quickEntry.queued);
        return true;
      } finally {
        setBusy(false);
      }
    },
    [queue, showToast],
  );

  const submitPhoto = useCallback(
    async (file: File): Promise<void> => {
      setBusy(true);
      const blob = await compressImage(file);
      const job = await queue.submit({ type: 'image', blob, mimeType: blob.type || 'image/webp' }).finally(() => setBusy(false));
      if (job.state === 'queued') showToast(job.queueReason === 'DATA_SAVER' ? MESSAGES.quickEntry.held : MESSAGES.quickEntry.queued);
    },
    [queue, showToast],
  );

  return { busy, submitText, submitPhoto, prepareModel };
}
