import { useWeeklyStats } from '@/features/dashboard';
import { MESSAGES } from '@/shared/messages/en';
import { Card } from '@/shared/ui/card';

/** F6 Stats: the trader's own numbers this week. */
export function StatsPanel() {
  const stats = useWeeklyStats();
  const share = stats?.onDeviceShare;
  return (
    <Card aria-labelledby="stats-heading">
      <h2 id="stats-heading" className="mb-3 font-bold">
        {MESSAGES.settings.stats}
      </h2>
      <dl className="grid grid-cols-3 gap-3 text-center">
        <div>
          <dt className="text-sm text-muted">{MESSAGES.settings.onDeviceShare}</dt>
          <dd className="amount text-2xl font-bold text-device" data-testid="device-share">
            {share === null || share === undefined ? '—' : `${Math.round(share * 100)}%`}
          </dd>
        </div>
        <div>
          <dt className="text-sm text-muted">{MESSAGES.settings.cloudCalls}</dt>
          <dd className="amount text-2xl font-bold text-cloud">{stats?.cloud ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted">{MESSAGES.inference.avoided(stats?.cloudCallsAvoided ?? 0).replace(/^\d+ /, '')}</dt>
          <dd className="amount text-2xl font-bold">{stats?.cloudCallsAvoided ?? '—'}</dd>
        </div>
      </dl>
    </Card>
  );
}
