import { Link, Outlet } from '@tanstack/react-router';
import { MESSAGES } from '@/shared/messages/en';
import { ToastHost } from '@/shared/ui/toast';
import { BottomNav } from './bottom-nav';
import { StatusPill } from './status-pill';
import { UpdatePrompt } from './update-prompt';

/** Layout: slim brand bar with network status, page content, bottom navigation. */
export function AppShell() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-2xl flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:p-3">
        Skip to content
      </a>
      <header className="sticky top-0 z-40 flex items-center justify-between gap-3 bg-bg px-4 pb-2 pt-[calc(0.75rem+env(safe-area-inset-top))]">
        <Link to="/" className="press flex min-h-11 items-center gap-2 text-lg font-bold tracking-[-0.01em] text-text">
          <img src="/favicon.svg" alt="" width={28} height={28} className="rounded-lg" />
          {MESSAGES.appName}
        </Link>
        <StatusPill />
      </header>
      <main id="main" className="flex-1 px-4 pb-32 pt-1">
        <Outlet />
      </main>
      <BottomNav />
      <ToastHost />
      <UpdatePrompt />
    </div>
  );
}
