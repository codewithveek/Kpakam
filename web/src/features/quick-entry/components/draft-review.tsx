import { useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import type { Draft } from '@/inference/domain/draft';
import { domainProblems } from '@/inference/domain/draft-validation';
import type { AiJob, JobOutput } from '@/inference/jobs/job';
import { parseUlid, newUlid, type Ulid } from '@/shared/ids/ulid';
import { MESSAGES } from '@/shared/messages/en';
import { speak } from '@/platform/tts';
import { Button } from '@/shared/ui/button';
import { SpeakerIcon } from '@/shared/ui/icons';
import type { SaveError } from '@/features/ledger';
import { DraftCard, describeForTrader } from './draft-card';
import { InferenceBadge } from '@/inference/components/inference-badge';

interface Row {
  readonly draft: Draft;
  readonly journalId: Ulid;
  readonly index: number;
}

const ERROR_TEXT: Readonly<Record<SaveError, string>> = {
  CUSTOMER_REQUIRED: MESSAGES.errors.customerRequired,
  PAID_EXCEEDS_TOTAL: MESSAGES.errors.paidExceedsTotal,
  AMOUNT_TOO_SMALL: MESSAGES.errors.amountTooSmall,
  ALREADY_REVERSED: MESSAGES.errors.alreadyReversed,
  INVARIANT_VIOLATION: MESSAGES.errors.saveFailed,
};

function problemOf(draft: Draft): string | null {
  if (!(draft.totalNaira > 0)) return MESSAGES.errors.amountTooSmall;
  const [first] = domainProblems(draft, 0);
  if (first === undefined) return null;
  return first.includes('customerName')
    ? MESSAGES.errors.customerRequired
    : first.includes('paidNaira')
      ? MESSAGES.errors.paidExceedsTotal
      : MESSAGES.errors.amountTooSmall;
}

function initialRows(job: AiJob, output: JobOutput): Row[] {
  return output.response.drafts.map((draft, index) => ({ draft, index, journalId: parseUlid(job.journalIds[index] ?? '') ?? newUlid() }));
}

/** Review step of F1: the trader confirms or edits drafts before anything is saved. */
export function DraftReview({ job, output }: { readonly job: AiJob; readonly output: JobOutput }) {
  const { ledger, queue } = useServices();
  const showToast = useAppStore((s) => s.showToast);
  const [rows, setRows] = useState<Row[]>(() => initialRows(job, output));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const problems = rows.map((row) => problemOf(row.draft));
  const update = (index: number, draft: Draft) => setRows(rows.map((r) => (r.index === index ? { ...r, draft } : r)));
  const discard = (index: number) => {
    const next = rows.filter((r) => r.index !== index);
    setRows(next);
    if (next.length === 0) void queue.markReviewed(job.id);
  };

  async function saveAll() {
    setSaving(true);
    const source = { inference: output.source, reason: output.reason, promptVersion: output.promptVersion };
    const result = await ledger.confirmDrafts(
      rows.map((r) => ({
        draft: r.draft,
        journalId: r.journalId,
        idempotencyKey: `${job.idempotencyKey}:${r.index}`,
        occurredAt: new Date(job.capturedAt),
        source,
      })),
    );
    setSaving(false);
    if (!result.ok) {
      setError(ERROR_TEXT[result.error]);
      return;
    }
    const saved = result.value;
    showToast(MESSAGES.drafts.saved(rows.length), {
      label: MESSAGES.drafts.undo,
      run: () => void Promise.all(saved.map((journal) => ledger.reverseEntry(journal))),
    });
    await queue.markReviewed(job.id);
  }

  return (
    <article className="flex flex-col gap-3" aria-label={MESSAGES.drafts.title}>
      <div className="flex flex-col gap-2">
        <h3 className="text-[20px] font-bold tracking-[-0.02em]">{MESSAGES.drafts.title}</h3>
        <InferenceBadge source={output.source} reason={output.reason} />
      </div>
      {output.response.transcript !== null && (
        <p className="rounded-[16px] bg-bg p-3 text-[15px] text-muted">
          <span className="font-semibold text-text">{MESSAGES.voice.transcript}:</span> “{output.response.transcript}”
        </p>
      )}
      {output.confidence === 'low' && <p className="rounded-[12px] bg-accent-soft p-3 text-sm font-medium text-accent">{MESSAGES.quickEntry.lowConfidence}</p>}
      <ul className="flex flex-col gap-2">
        {rows.map((row, i) => (
          <DraftCard
            key={row.index}
            draft={row.draft}
            problem={problems[i] ?? null}
            onChange={(d) => update(row.index, d)}
            onDiscard={() => discard(row.index)}
          />
        ))}
      </ul>
      {error !== null && (
        <p role="alert" className="font-semibold text-danger">
          {error}
        </p>
      )}
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          icon={<SpeakerIcon />}
          aria-label={MESSAGES.voice.readBack}
          onClick={() => speak(rows.map((r) => describeForTrader(r.draft)).join('. '))}
        />
        <Button size="lg" className="flex-1" onClick={() => void saveAll()} disabled={saving || rows.length === 0 || problems.some((p) => p !== null)}>
          {MESSAGES.drafts.saveAll(rows.length)}
        </Button>
      </div>
    </article>
  );
}
