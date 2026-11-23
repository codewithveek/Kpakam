import { Link, useNavigate } from '@tanstack/react-router';
import type { ComponentType, SVGProps } from 'react';
import { MESSAGES } from '@/shared/messages/en';
import { EditIcon, HomeIcon, ListIcon, PeopleIcon, SettingsIcon } from '@/shared/ui/icons';

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

function Tab({ to, label, Icon }: { readonly to: NavPath; readonly label: string; readonly Icon: IconType }) {
  return (
    <li className="flex-1">
      <Link
        to={to}
        activeOptions={{ exact: to === '/' }}
        activeProps={{ 'aria-current': 'page' }}
        className="group press flex min-h-16 flex-col items-center justify-center gap-1 text-[12px] font-semibold text-muted aria-[current=page]:text-text"
      >
        <span className="flex h-8 w-14 items-center justify-center rounded-full transition-colors duration-200 group-aria-[current=page]:bg-device-soft group-aria-[current=page]:text-device">
          <Icon width={22} height={22} />
        </span>
        <span className="max-w-full truncate px-1">{label}</span>
      </Link>
    </li>
  );
}

/** Focuses the note box on Today: the one action traders take most. */
function RecordButton() {
  const navigate = useNavigate();
  async function record() {
    await navigate({ to: '/' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
    document.getElementById('quick-entry-input')?.focus();
  }
  return (
    <li className="flex flex-1 justify-center">
      <button type="button" onClick={() => void record()} className="press -mt-5 flex flex-col items-center gap-1 text-[12px] font-semibold text-text">
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-primary text-on-primary shadow-float">
          <EditIcon width={24} height={24} />
        </span>
        {MESSAGES.nav.record}
      </button>
    </li>
  );
}

/** Bottom navigation: four places plus a labelled centre action (never an icon-only "+"). */
export function BottomNav() {
  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface pb-[env(safe-area-inset-bottom)]">
      <ul className="mx-auto flex max-w-2xl items-end px-2">
        {LEFT.map((item) => (
          <Tab key={item.to} {...item} />
        ))}
        <RecordButton />
        {RIGHT.map((item) => (
          <Tab key={item.to} {...item} />
        ))}
      </ul>
    </nav>
  );
}
