import { useAppStore } from './app-store';
import { MESSAGES } from '@/shared/messages/en';

/** Network status as a plain word on a soft colour (green online, orange offline), never colour alone. */
export function StatusPill() {
  const online = useAppStore((s) => s.online);
  return (
    <p
      role="status"
      data-testid="network-status"
      data-online={online}
      className={`inline-flex min-h-9 items-center rounded-full px-4 text-[13px] font-semibold ${online ? 'bg-income-soft text-income' : 'bg-warn-soft text-warn'}`}
    >
      <span>{online ? MESSAGES.status.online : 'Offline'}</span>
      {!online && <span className="sr-only">{MESSAGES.status.offline}</span>}
    </p>
  );
}
