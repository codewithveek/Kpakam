import { useEffect } from 'react';
import { useAppStore } from '@/app/app-store';

const TOAST_MS = 3500;

/** One polite live-region toast at a time. */
export function ToastHost() {
  const toast = useAppStore((s) => s.toast);
  const clear = useAppStore((s) => s.clearToast);
  useEffect(() => {
    if (toast === null) return undefined;
    const timer = setTimeout(clear, TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast, clear]);
  return (
    <div role="status" aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center px-4">
      {toast !== null && (
        <p key={toast.id} className="pointer-events-auto rounded-xl bg-text px-4 py-3 font-medium text-bg shadow-lg">
          {toast.text}
        </p>
      )}
    </div>
  );
}
