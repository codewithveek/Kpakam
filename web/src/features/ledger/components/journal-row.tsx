import { useId, useState } from 'react';
import { DraftEditor } from '@/inference/components/draft-editor';
import { InferenceBadge } from '@/inference/components/inference-badge';
import type { Draft } from '@/inference/domain/draft';
import { MESSAGES } from '@/shared/messages/en';
import { formatLagosTime } from '@/shared/time/lagos';
import { Button } from '@/shared/ui/button';
import { ClockIcon, CloudIcon, EditIcon, PhoneIcon, UndoIcon } from '@/shared/ui/icons';
import { KindAvatar } from '@/shared/ui/kind-avatar';
import { RowContent } from '@/shared/ui/list';
import { journalToDraft } from '../domain/journal-from-draft';
import type { Journal } from '../domain/journal';
import type { JournalEntry } from '../repository/journal-repository';
import { useEntryActions } from '../hooks/use-entry-actions';
import { creditLabel, EntryAmount } from './entry-amount';

export { headlineAmount } from './entry-amount';

export interface JournalRowProps {
  readonly entry: JournalEntry;
  readonly reversed: boolean;
  readonly customerName: string | null;
  /** True for entries saved after the list mounted: they get a one-off highlight. */
  readonly isNew?: boolean;
}

function Subtitle({ journal, pendingSync }: { readonly journal: Journal; readonly pendingSync: boolean }) {
  const source = journal.source.inference;
  const credit = creditLabel(journal);
  return (
    <>
      <span className="truncate">
        {credit === null ? MESSAGES.ledger.kind[journal.kind] : <span className="font-semibold text-credit">{credit}</span>} ·{' '}
        {formatLagosTime(journal.occurredAt)}
      </span>
      {source === 'on_device' && <PhoneIcon width={14} height={14} aria-label={MESSAGES.inference.onDevice} role="img" />}
      {source === 'in_cloud' && <CloudIcon width={14} height={14} aria-label={MESSAGES.inference.cloud} role="img" />}
      {pendingSync && (
        <span className="inline-flex items-center gap-1 font-semibold text-accent" data-testid="pending-sync">
          <ClockIcon width={14} height={14} />
          {MESSAGES.status.pendingSync}
        </span>
      )}
    </>
  );
}

function titleOf(journal: Journal, customerName: string | null): string {
  if (journal.kind === 'reversal') return MESSAGES.ledger.reversal;
  return customerName ?? journal.description.charAt(0).toUpperCase() + journal.description.slice(1);
}

function FixForm({ journal, draft, onDone }: { readonly journal: Journal; readonly draft: Draft; readonly onDone: () => void }) {
  const { fix } = useEntryActions();
  const [value, setValue] = useState(draft);
  return (
    <div className="flex flex-col gap-3 pt-1">
      <DraftEditor draft={value} onChange={setValue} />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onDone}>
          {MESSAGES.settings.cancel}
        </Button>
        <Button onClick={() => void fix(journal, value).then((ok) => ok && onDone())}>{MESSAGES.drafts.save}</Button>
      </div>
    </div>
  );
}

function Details({ journal, editable, customerName }: { readonly journal: Journal; readonly editable: boolean; readonly customerName: string | null }) {
  const { undo } = useEntryActions();
  const [draft, setDraft] = useState<Draft | null>(null);
  const source = journal.source.inference;
  return (
    <div className="reveal flex flex-col gap-3 px-4 pb-4">
      {source !== 'manual' && <InferenceBadge source={source} reason={journal.source.reason as 'DEFAULT_DEVICE'} />}
      {journal.kind !== 'reversal' && <p className="text-sm text-muted">{journal.description}</p>}
      {editable && draft === null && (
        <div className="flex gap-2">
          <Button variant="secondary" icon={<UndoIcon width={18} height={18} />} onClick={() => void undo(journal)}>
            {MESSAGES.ledger.undo}
          </Button>
          <Button variant="secondary" icon={<EditIcon width={18} height={18} />} onClick={() => setDraft(journalToDraft(journal, customerName))}>
            {MESSAGES.ledger.edit}
          </Button>
        </div>
      )}
      {draft !== null && <FixForm journal={journal} draft={draft} onDone={() => setDraft(null)} />}
    </div>
  );
}

/** One entry: summary first; tap for the source, Undo, and Fix (summary in the list, detail on tap). */
export function JournalRow({ entry, reversed, customerName, isNew = false }: JournalRowProps) {
  const { journal, pendingSync } = entry;
  const [open, setOpen] = useState(false);
  const detailsId = useId();
  return (
    <li className={`overflow-hidden rounded-[24px] bg-surface ${isNew ? 'flash-new' : ''}`} data-testid="journal-row" data-kind={journal.kind}>
      <button type="button" className="press w-full" aria-expanded={open} aria-controls={detailsId} onClick={() => setOpen(!open)}>
        <RowContent
          leading={<KindAvatar kind={journal.kind} />}
          title={titleOf(journal, customerName)}
          subtitle={<Subtitle journal={journal} pendingSync={pendingSync} />}
          trailing={<EntryAmount journal={journal} reversed={reversed} />}
        />
      </button>
      <div id={detailsId}>{open && <Details journal={journal} editable={journal.kind !== 'reversal' && !reversed} customerName={customerName} />}</div>
    </li>
  );
}
