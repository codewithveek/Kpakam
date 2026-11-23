import { MESSAGES } from '@/shared/messages/en';
import { ClockIcon, CloudIcon, PhoneIcon } from '@/shared/ui/icons';
import type { QueueReason } from '@/inference/domain/route-decision';

export type BadgeSource = 'on_device' | 'in_cloud' | 'queued';

export interface InferenceBadgeProps {
  readonly source: BadgeSource;
  readonly reason: keyof typeof MESSAGES.inference.reason;
  readonly queueReason?: QueueReason | null;
}

const STYLE: Readonly<Record<BadgeSource, string>> = {
  on_device: 'bg-device-soft text-device',
  in_cloud: 'bg-cloud-soft text-cloud',
  queued: 'bg-accent-soft text-accent',
};

/**
 * F6: shows where an AI decision ran and why. Never colour alone: icon + text.
 */
export function InferenceBadge({ source, reason, queueReason = null }: InferenceBadgeProps) {
  const label = source === 'on_device' ? MESSAGES.inference.onDevice : source === 'in_cloud' ? MESSAGES.inference.cloud : MESSAGES.inference.queued;
  const Icon = source === 'on_device' ? PhoneIcon : source === 'in_cloud' ? CloudIcon : ClockIcon;
  const why = queueReason === null ? MESSAGES.inference.reason[reason] : MESSAGES.inference.queueReason[queueReason];
  return (
    <span
      className={`inline-flex min-h-8 max-w-full items-center gap-1.5 self-start rounded-full px-3 py-1 text-[13px] font-semibold ${STYLE[source]}`}
      data-testid="inference-badge"
      data-source={source}
      data-reason={reason}
    >
      <Icon width={16} height={16} />
      <span>{label}</span>
      <span className="truncate font-medium opacity-90">· {why}</span>
    </span>
  );
}
