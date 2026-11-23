import { useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { InferenceBadge } from '@/inference/components/inference-badge';
import type { QueueReason } from '@/inference/domain/route-decision';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { SendIcon } from '@/shared/ui/icons';
import { PageHeader } from '@/shared/ui/page-header';
import { useWeekData } from '../hooks/use-week-data';

const CACHE_KEY = 'kpakam.insights';

function readCache(): string | null {
  try {
    return localStorage.getItem(CACHE_KEY);
  } catch {
    return null;
  }
}

function writeCache(text: string): void {
  try {
    localStorage.setItem(CACHE_KEY, text);
  } catch {
    // Storage blocked: the report still shows for this visit.
  }
}

/** S2: a short weekly report, generated in the cloud and cached for offline reading. */
export function InsightsPage() {
  const { textTasks, remoteConfig } = useServices();
  const showToast = useAppStore((s) => s.showToast);
  const week = useWeekData();
  const [text, setText] = useState<string | null>(readCache);
  const [held, setHeld] = useState<QueueReason | null>(null);
  const [busy, setBusy] = useState(false);

  async function generate(approved: boolean) {
    setBusy(true);
    const result = await textTasks.weeklyInsights(week, { approved, onChunk: setText });
    setBusy(false);
    if (result.ok) {
      writeCache(result.value);
      setHeld(null);
    } else if (result.error.type === 'held') setHeld(result.error.queueReason);
    else showToast(MESSAGES.errors.generic);
  }

  if (!remoteConfig.values().insightsEnabled) return <p>{MESSAGES.insights.disabled}</p>;
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={MESSAGES.insights.title} />
      {held !== null && (
        <Card className="flex flex-col gap-3">
          <InferenceBadge source="queued" reason="LONG_CONTEXT" queueReason={held} />
          {held === 'DATA_SAVER' && (
            <Button icon={<SendIcon />} onClick={() => void generate(true)}>
              {MESSAGES.settings.sendNow} ({MESSAGES.settings.estimatedSize(Math.max(1, Math.round(JSON.stringify(week).length / 1024) + 3))})
            </Button>
          )}
        </Card>
      )}
      <Button onClick={() => void generate(false)} disabled={busy}>
        {MESSAGES.insights.generate}
      </Button>
      {text !== null && (
        <Card aria-live="polite">
          <p className="mb-2 text-sm text-muted">{busy ? MESSAGES.quickEntry.working : MESSAGES.insights.cached}</p>
          <div className="whitespace-pre-wrap leading-relaxed">{text}</div>
        </Card>
      )}
    </div>
  );
}
