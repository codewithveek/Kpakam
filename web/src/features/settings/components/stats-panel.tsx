import { useWeeklyStats } from '@/features/dashboard';
import { MESSAGES } from '@/shared/messages/en';
import { CloudIcon, PhoneIcon } from '@/shared/ui/icons';

/** F6 Stats: the trader's own numbers this week, with the on-device share as a bar. */
export function StatsPanel() {
  const stats = useWeeklyStats() ?? { onDevice: 0, cloud: 0, cloudCallsAvoided: 0, onDeviceShare: null };
  const pct = stats.onDeviceShare === null ? null : Math.round(stats.onDeviceShare * 100);
  return (
    <div className="rounded-[20px] border border-border bg-surface p-4 shadow-card">
      <div className="flex items-end justify-between">
        <div>
          <p className="text-[13px] font-medium text-muted">{MESSAGES.settings.onDeviceShare}</p>
          <p className="amount text-[32px] font-bold leading-tight tracking-[-0.02em] text-device" data-testid="device-share">
            {pct === null ? '—' : `${pct}%`}
          </p>
        </div>
        <p className="pb-1 text-right text-[13px] text-muted">{MESSAGES.inference.avoided(stats.cloudCallsAvoided)}</p>
      </div>
      <div
        className="mt-3 h-2.5 overflow-hidden rounded-full bg-cloud-soft"
        role="img"
        aria-label={pct === null ? MESSAGES.settings.onDeviceShare : `${pct}% ${MESSAGES.settings.onDeviceShare}`}
      >
        <div className="h-full rounded-full bg-device transition-[width] duration-500" style={{ width: `${pct ?? 0}%` }} />
      </div>
      <div className="mt-3 flex justify-between text-[13px] font-semibold">
        <span className="inline-flex items-center gap-1.5 text-device">
          <PhoneIcon width={15} height={15} />
          {MESSAGES.inference.onDevice} {stats.onDevice}
        </span>
        <span className="inline-flex items-center gap-1.5 text-cloud">
          <CloudIcon width={15} height={15} />
          {MESSAGES.settings.cloudCalls} {stats.cloud}
        </span>
      </div>
    </div>
  );
}
