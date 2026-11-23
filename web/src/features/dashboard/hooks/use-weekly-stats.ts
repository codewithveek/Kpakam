import { useEffect, useState } from 'react';
import { useServices } from '@/app/services';
import type { RoutingStats } from '@/inference/ai-event-repository';
import { lagosDay, weekStart } from '@/shared/time/lagos';

/** Live routing numbers for this Lagos week, from `aiEvents`: on-device share and cloud calls avoided (F6). */
export function useWeeklyStats(): RoutingStats | null {
  const { stats } = useServices();
  const [value, setValue] = useState<RoutingStats | null>(null);
  useEffect(() => {
    const monday = new Date(`${weekStart(lagosDay(new Date()))}T00:00:00+01:00`);
    return stats.watchStats(monday, setValue);
  }, [stats]);
  return value;
}
