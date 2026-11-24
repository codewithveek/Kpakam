import { useRegisterSW } from 'virtual:pwa-register/react';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';

/** Shows "update" when a new service worker is waiting (vite-plugin-pwa prompt mode). */
export function UpdatePrompt() {
  const {
    needRefresh: [needRefresh],
    updateServiceWorker,
  } = useRegisterSW();
  if (!needRefresh) return null;
  return (
    <div
      role="alert"
      className="fixed inset-x-4 bottom-24 z-50 mx-auto flex max-w-md items-center justify-between gap-3 rounded-2xl border border-border bg-surface p-3 shadow-lg"
    >
      <span>{MESSAGES.status.updateReady}</span>
      <Button onClick={() => void updateServiceWorker(true)}>{MESSAGES.status.updateNow}</Button>
    </div>
  );
}
