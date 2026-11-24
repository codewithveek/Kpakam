import { Link } from '@tanstack/react-router';
import type { ComponentType, SVGProps } from 'react';
import { MESSAGES } from '@/shared/messages/en';
import { HomeIcon, ListIcon, MicIcon, PeopleIcon, SettingsIcon } from '@/shared/ui/icons';
import { useAppStore } from './app-store';

type NavPath = '/' | '/history' | '/customers' | '/settings';
type IconType = ComponentType<SVGProps<SVGSVGElement>>;

const LEFT: readonly { to: NavPath; label: string; Icon: IconType }[] = [
  { to: '/', label: MESSAGES.nav.today, Icon: HomeIcon },
  { to: '/history', label: MESSAGES.nav.history, Icon: ListIcon },
];
const RIGHT: readonly { to: NavPath; label: string; Icon: IconType }[] = [
  { to: '/customers', label: MESSAGES.nav.customers, Icon: PeopleIcon },
  { to: '/settings', label: MESSAGES.nav.settings, Icon: SettingsIcon },
];

const ITEM = 'press flex h-16 w-full flex-col items-center justify-center gap-0.5 text-[12px] font-medium';

function Tab({ to, label, Icon }: { readonly to: NavPath; readonly label: string; readonly Icon: IconType }) {
  return (
    <li>
      <Link
        to={to}
        activeOptions={{ exact: to === '/' }}
        activeProps={{ 'aria-current': 'page' }}
        className={`${ITEM} text-muted aria-[current=page]:font-semibold aria-[current=page]:text-text`}
      >
        <span className="flex h-8 items-center">
          <Icon width={22} height={22} />
        </span>
        <span>{label}</span>
      </Link>
    </li>
  );
}

/** Speak sits in the middle of the bar; its icon is the one black tile, so the main action is easy to find. */
function SpeakButton() {
  const open = useAppStore((s) => s.setVoiceOpen);
  return (
    <li>
      <button type="button" onClick={() => open(true)} className={`${ITEM} text-text`}>
        <span className="flex h-8 w-12 items-center justify-center rounded-full bg-ink text-on-ink">
          <MicIcon width={18} height={18} />
        </span>
        <span className="font-semibold">{MESSAGES.nav.record}</span>
      </button>
    </li>
  );
}

/** A docked, full-width bottom bar: five equal slots with short labels, so nothing ever squeezes. */
export function BottomNav() {
  return (
    <nav aria-label={MESSAGES.nav.label} className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface pb-[env(safe-area-inset-bottom)]">
      <ul className="mx-auto grid max-w-2xl grid-cols-5 px-2">
        {LEFT.map((item) => (
          <Tab key={item.to} {...item} />
        ))}
        <SpeakButton />
        {RIGHT.map((item) => (
          <Tab key={item.to} {...item} />
        ))}
      </ul>
    </nav>
  );
}
