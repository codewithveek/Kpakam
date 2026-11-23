import { Link, Outlet } from '@tanstack/react-router';
import type { ComponentType, SVGProps } from 'react';
import { MESSAGES } from '@/shared/messages/en';
import { HomeIcon, ListIcon, PeopleIcon, SettingsIcon } from '@/shared/ui/icons';
import { ToastHost } from '@/shared/ui/toast';
import { StatusPill } from './status-pill';
import { UpdatePrompt } from './update-prompt';

type NavPath = '/' | '/history' | '/customers' | '/settings';

const NAV: readonly { to: NavPath; label: string; Icon: ComponentType<SVGProps<SVGSVGElement>> }[] = [
  { to: '/', label: MESSAGES.nav.today, Icon: HomeIcon },
  { to: '/history', label: MESSAGES.nav.history, Icon: ListIcon },
  { to: '/customers', label: MESSAGES.nav.customers, Icon: PeopleIcon },
  { to: '/settings', label: MESSAGES.nav.settings, Icon: SettingsIcon },
];

/** Layout: header with status, page content, bottom navigation. */
export function AppShell() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-2xl flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:p-3">
        Skip to content
      </a>
      <header className="sticky top-0 z-40 flex items-center justify-between gap-3 border-b border-border bg-bg/95 px-4 py-3 backdrop-blur">
        <Link to="/" className="flex items-center gap-2 text-xl font-extrabold tracking-tight text-primary">
          <img src="/favicon.svg" alt="" width={28} height={28} />
          {MESSAGES.appName}
        </Link>
        <StatusPill />
      </header>
      <main id="main" className="flex-1 px-4 pb-28 pt-4">
        <Outlet />
      </main>
      <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface/95 backdrop-blur">
        <ul className="mx-auto grid max-w-2xl grid-cols-4">
          {NAV.map(({ to, label, Icon }) => (
            <li key={to}>
              <Link
                to={to}
                activeOptions={{ exact: to === '/' }}
                className="flex min-h-16 flex-col items-center justify-center gap-0.5 text-xs font-semibold text-muted data-[status=active]:text-primary"
                activeProps={{ 'aria-current': 'page' }}
              >
                <Icon width={22} height={22} />
                <span>{label}</span>
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      <ToastHost />
      <UpdatePrompt />
    </div>
  );
}
