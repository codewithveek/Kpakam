import { Link, Outlet, useRouterState } from '@tanstack/react-router';
import { VoiceScreen } from '@/features/quick-entry';
import { MESSAGES } from '@/shared/messages/en';
import { ToastHost } from '@/shared/ui/toast';
import { useAppStore } from './app-store';
import { BottomNav } from './bottom-nav';
import { StatusPill } from './status-pill';
import { UpdatePrompt } from './update-prompt';

/** Detail pages (with a Back button) hide the main bar; they float their own main action instead. */
const DETAIL = /^\/(customers\/[^/]+|insights)\/?$/;

/** Layout: a slim top row (brand mark, network status), the page, and a docked bottom bar. */
export function AppShell() {
  const voiceOpen = useAppStore((s) => s.voiceOpen);
  const detail = useRouterState({ select: (s) => DETAIL.test(s.location.pathname) });
  return (
    <div className="mx-auto flex min-h-dvh max-w-2xl flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:p-3">
        Skip to content
      </a>
      <header className="flex items-center justify-between gap-3 px-4 pb-1 pt-[calc(0.75rem+env(safe-area-inset-top))]">
        <Link to="/" aria-label={MESSAGES.appName} className="press flex h-12 w-12 items-center justify-center rounded-full border border-border bg-surface">
          <img src="/favicon.svg" alt="" width={26} height={26} className="rounded-[8px]" />
        </Link>
        <StatusPill />
      </header>
      <main id="main" className="flex-1 px-4 pb-32 pt-2">
        <Outlet />
      </main>
      {!detail && <BottomNav />}
      <ToastHost />
      <UpdatePrompt />
      {voiceOpen && <VoiceScreen />}
    </div>
  );
}
