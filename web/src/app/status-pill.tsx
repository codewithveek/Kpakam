import { useAppStore } from './app-store';
import { MESSAGES } from '@/shared/messages/en';
import { OfflineIcon, OnlineIcon } from '@/shared/ui/icons';

/** Network status with icon and text, never colour alone. */
export function StatusPill() {
  const online = useAppStore((s) => s.online);
  return (
    <p
      role="status"
      data-testid="network-status"
      data-online={online}
      className={`inline-flex min-h-11 items-center gap-1.5 rounded-full px-4 text-[13px] font-semibold ${online ? 'border border-border bg-surface text-text' : 'bg-ink text-on-ink'}`}
    >
      {online ? <OnlineIcon width={16} height={16} /> : <OfflineIcon width={16} height={16} />}
      <span>{online ? MESSAGES.status.online : 'Offline'}</span>
      {!online && <span className="sr-only">{MESSAGES.status.offline}</span>}
    </p>
  );
}
