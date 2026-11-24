import { useState } from 'react';
import { DraftEditor } from '@/inference/components/draft-editor';
import type { Draft } from '@/inference/domain/draft';
import { formatDraftNaira } from '@/shared/money/format';
import { MESSAGES } from '@/shared/messages/en';
import { CheckIcon, CloseIcon, EditIcon } from '@/shared/ui/icons';
import { KindAvatar } from '@/shared/ui/kind-avatar';

export interface DraftCardProps {
  readonly draft: Draft;
  readonly problem: string | null;
  readonly onChange: (draft: Draft) => void;
  readonly onDiscard: () => void;
}

/** Plain-words summary of a draft, read aloud by text-to-speech. */
export function describeForTrader(draft: Draft): string {
  const parts: string[] = [MESSAGES.drafts.kind[draft.kind], formatDraftNaira(draft.totalNaira)];
  if (draft.item !== null) parts.push([draft.quantity, draft.unit, draft.item].filter((p) => p !== null).join(' '));
  if (draft.kind === 'credit_sale') parts.push(owesLine(draft));
  else if (draft.customerName !== null) parts.push(draft.customerName);
  return parts.join(' · ');
}

function owesLine(draft: Draft): string {
  return `${draft.customerName ?? '?'} ${MESSAGES.drafts.owes.toLowerCase()} ${formatDraftNaira(draft.totalNaira - (draft.paidNaira ?? 0))}`;
}

function detailLine(draft: Draft): string {
  const goods = [draft.quantity, draft.unit, draft.item].filter((p) => p !== null).join(' ');
  return [goods, draft.kind === 'credit_sale' ? null : draft.customerName].filter((p) => p !== null && p !== '').join(' · ');
}

const iconButton = 'press flex h-11 w-11 items-center justify-center rounded-full bg-surface text-text';

/** One draft in the review list: what it is, how much, and who still owes. Edit or discard before saving. */
export function DraftCard({ draft, problem, onChange, onDiscard }: DraftCardProps) {
  const [editing, setEditing] = useState(false);
  const detail = detailLine(draft);
  return (
    <li className="rounded-[22px] bg-surface-2 p-4" data-testid="draft">
      <div className="flex items-center gap-3">
        <KindAvatar kind={draft.kind} />
        <div className="min-w-0 flex-1">
          <p className="text-[15px] font-semibold">{MESSAGES.drafts.kind[draft.kind]}</p>
          {detail !== '' && <p className="truncate text-[13px] text-muted">{detail}</p>}
        </div>
        <p className="amount shrink-0 text-[17px] font-semibold">{formatDraftNaira(draft.totalNaira)}</p>
      </div>
      {draft.kind === 'credit_sale' && <p className="mt-2 rounded-full bg-accent-soft px-3 py-1.5 text-[13px] font-semibold text-credit">{owesLine(draft)}</p>}
      {problem !== null && (
        <p className="mt-2 text-sm font-semibold text-danger" role="alert">
          {problem}
        </p>
      )}
      <div className="mt-3 flex justify-end gap-2">
        <button
          type="button"
          className={iconButton}
          onClick={() => setEditing(!editing)}
          aria-label={editing ? MESSAGES.drafts.done : MESSAGES.drafts.edit}
          aria-expanded={editing}
        >
          {editing ? <CheckIcon width={20} height={20} /> : <EditIcon width={20} height={20} />}
        </button>
        <button type="button" className={iconButton} onClick={onDiscard} aria-label={MESSAGES.drafts.discard}>
          <CloseIcon width={20} height={20} />
        </button>
      </div>
      {editing && (
        <div className="reveal mt-2">
          <DraftEditor draft={draft} onChange={onChange} />
        </div>
      )}
    </li>
  );
}
