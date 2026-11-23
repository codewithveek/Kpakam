import { useState } from 'react';
import { DraftEditor } from '@/inference/components/draft-editor';
import { InferenceBadge } from '@/inference/components/inference-badge';
import { formatNaira } from '@/shared/money/format';
import { asKobo } from '@/shared/money/kobo';
import { MESSAGES } from '@/shared/messages/en';
import { formatLagosTime } from '@/shared/time/lagos';
import { Button } from '@/shared/ui/button';
import { ClockIcon, EditIcon, UndoIcon } from '@/shared/ui/icons';
import type { Draft } from '@/inference/domain/draft';
import { journalToDraft } from '../domain/journal-from-draft';
import type { Journal } from '../domain/journal';
import type { JournalEntry } from '../repository/journal-repository';
import { useEntryActions } from '../hooks/use-entry-actions';

/** The headline amount: what the trader thinks of as "the amount" for each kind. */
export function headlineAmount(journal: Journal): number {
  const sum = (match: (account: string) => boolean) => journal.lines.filter((l) => match(l.account)).reduce((s, l) => s + l.amountKobo, 0);
  switch (journal.kind) {
    case 'sale':
    case 'credit_sale':
      return -sum((a) => a === 'sales');
    case 'expense':
      return sum((a) => a === 'expenses');
    case 'repayment':
      return sum((a) => a === 'cash');
    case 'reversal':
      return Math.abs(journal.lines.reduce((s, l) => s + Math.abs(l.amountKobo), 0) / 2);
  }
}

export interface JournalRowProps {
  readonly entry: JournalEntry;
  readonly reversed: boolean;
  readonly customerName: string | null;
}

/** One history entry with pending-sync mark, inference source, Undo and Fix. */
export function JournalRow({ entry, reversed, customerName }: JournalRowProps) {
  const { journal, pendingSync } = entry;
  const { undo, fix } = useEntryActions();
  const [draft, setDraft] = useState<Draft | null>(null);
  const editable = journal.kind !== 'reversal' && !reversed;
  const source = journal.source.inference;
  return (
    <li className={`rounded-xl border border-border bg-surface p-3 ${reversed ? 'opacity-60' : ''}`} data-testid="journal-row" data-kind={journal.kind}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold">
            {MESSAGES.ledger.kind[journal.kind]}
            {customerName !== null ? ` · ${customerName}` : ''}
          </p>
          <p className="truncate text-sm text-muted">
            {journal.description} · {formatLagosTime(journal.occurredAt)}
          </p>
        </div>
        <p className={`amount shrink-0 text-lg font-bold ${reversed ? 'line-through' : ''}`}>{formatNaira(asKobo(headlineAmount(journal)))}</p>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
        {pendingSync && (
          <span className="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2 py-0.5 font-semibold text-accent" data-testid="pending-sync">
            <ClockIcon width={14} height={14} />
            {MESSAGES.status.pendingSync}
          </span>
        )}
        {reversed && <span className="rounded-full bg-surface-2 px-2 py-0.5 font-semibold">{MESSAGES.ledger.reversed}</span>}
        {source !== 'manual' && <InferenceBadge source={source} reason={journal.source.reason as 'DEFAULT_DEVICE'} />}
        {editable && (
          <span className="ml-auto flex gap-1">
            <Button variant="ghost" icon={<UndoIcon />} onClick={() => window.confirm(MESSAGES.ledger.confirmUndo) && void undo(journal)}>
              {MESSAGES.ledger.undo}
            </Button>
            <Button variant="ghost" icon={<EditIcon />} onClick={() => setDraft(journalToDraft(journal, customerName))}>
              {MESSAGES.ledger.edit}
            </Button>
          </span>
        )}
      </div>
      {draft !== null && (
        <div className="mt-3 flex flex-col gap-3 border-t border-border pt-3">
          <DraftEditor draft={draft} onChange={setDraft} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setDraft(null)}>
              {MESSAGES.settings.cancel}
            </Button>
            <Button onClick={() => void fix(journal, draft).then((ok) => ok && setDraft(null))}>{MESSAGES.drafts.save}</Button>
          </div>
        </div>
      )}
    </li>
  );
}
