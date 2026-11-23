import { useEffect } from 'react';
import { useAppStore } from '@/app/app-store';

const TOAST_MS = 3500;
const TOAST_WITH_ACTION_MS = 5000;

/** One polite live-region toast at a time, above the bottom navigation. Pauses while the tab is hidden. */
export function ToastHost() {
  const toast = useAppStore((s) => s.toast);
  const clear = useAppStore((s) => s.clearToast);
  useEffect(() => {
    if (toast === null) return undefined;
    let timer = setTimeout(clear, toast.action === null ? TOAST_MS : TOAST_WITH_ACTION_MS);
    const onVisibility = () => {
      clearTimeout(timer);
      if (document.visibilityState === 'visible') timer = setTimeout(clear, TOAST_MS);
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [toast, clear]);
  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(6rem+env(safe-area-inset-bottom))] z-50 flex justify-center px-4"
    >
      {toast !== null && (
        <div
          key={toast.id}
          className="enter-toast pointer-events-auto flex min-h-12 max-w-md items-center gap-3 rounded-full bg-text py-1.5 pl-5 pr-1.5 text-[15px] font-medium text-bg shadow-float"
        >
          <span className="py-1.5">{toast.text}</span>
          {toast.action !== null && (
            <button
              type="button"
              className="press min-h-10 rounded-full bg-bg/15 px-4 font-semibold text-bg"
              onClick={() => {
                toast.action?.run();
                clear();
              }}
            >
              {toast.action.label}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
