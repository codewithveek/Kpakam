import { useState } from 'react';
import type { Draft } from '@/inference/domain/draft';
import { formatDraftNaira } from '@/shared/money/format';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { CheckIcon, CloseIcon, EditIcon } from '@/shared/ui/icons';
import { DraftEditor } from '@/inference/components/draft-editor';

export interface DraftCardProps {
  readonly draft: Draft;
  readonly problem: string | null;
  readonly onChange: (draft: Draft) => void;
  readonly onDiscard: () => void;
}

/** Plain-words summary of a draft, e.g. "Sold on credit · ₦45,000 · Mama Bisi still owes ₦10,000". */
export function describeForTrader(draft: Draft): string {
  const parts: string[] = [MESSAGES.drafts.kind[draft.kind], formatDraftNaira(draft.totalNaira)];
  if (draft.item !== null) parts.push([draft.quantity, draft.unit, draft.item].filter((p) => p !== null).join(' '));
  if (draft.kind === 'credit_sale') {
    parts.push(`${draft.customerName ?? '?'} ${MESSAGES.drafts.owes.toLowerCase()} ${formatDraftNaira(draft.totalNaira - (draft.paidNaira ?? 0))}`);
  } else if (draft.customerName !== null) {
    parts.push(draft.customerName);
  }
  return parts.join(' · ');
}

/** One draft in the review list: summary, edit form, discard. */
export function DraftCard({ draft, problem, onChange, onDiscard }: DraftCardProps) {
  const [editing, setEditing] = useState(false);
  return (
    <li className="rounded-xl border border-border bg-bg p-3" data-testid="draft">
      <div className="flex items-start justify-between gap-2">
        <p className="amount text-lg font-semibold leading-snug">{describeForTrader(draft)}</p>
        <div className="flex shrink-0 gap-1">
          <Button
            variant="ghost"
            icon={editing ? <CheckIcon /> : <EditIcon />}
            onClick={() => setEditing(!editing)}
            aria-label={editing ? MESSAGES.drafts.done : MESSAGES.drafts.edit}
            aria-expanded={editing}
          />
          <Button variant="ghost" icon={<CloseIcon />} onClick={onDiscard} aria-label={MESSAGES.drafts.discard} />
        </div>
      </div>
      {problem !== null && (
        <p className="mt-1 text-sm font-semibold text-danger" role="alert">
          {problem}
        </p>
      )}
      {editing && (
        <div className="mt-3">
          <DraftEditor draft={draft} onChange={onChange} />
        </div>
      )}
    </li>
  );
}
