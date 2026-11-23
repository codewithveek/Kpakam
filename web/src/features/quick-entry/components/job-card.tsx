import { useServices } from '@/app/services';
import type { AiJob } from '@/inference/jobs/job';
import { estimatedSizeKb } from '@/inference/jobs/job';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { SendIcon } from '@/shared/ui/icons';
import { DraftReview } from './draft-review';
import { InferenceBadge } from '@/inference/components/inference-badge';

function inputPreview(job: AiJob): string {
  if (job.input.type === 'text') return `“${job.input.text}”`;
  return job.input.type === 'image' ? MESSAGES.quickEntry.photo : MESSAGES.voice.transcript;
}

function Waiting({ job }: { readonly job: AiJob }) {
  const { queue } = useServices();
  const held = job.queueReason === 'DATA_SAVER';
  return (
    <div className="flex flex-col gap-2">
      <InferenceBadge source="queued" reason={job.decision.reason} queueReason={job.queueReason} />
      <p className="text-muted">{inputPreview(job)}</p>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={() => void queue.cancel(job.id)}>
          {MESSAGES.settings.cancel}
        </Button>
        {held && (
          <Button icon={<SendIcon />} onClick={() => void queue.approve(job.id)}>
            {MESSAGES.settings.sendNow} ({MESSAGES.settings.estimatedSize(estimatedSizeKb(job.input))})
          </Button>
        )}
      </div>
    </div>
  );
}

function Failed({ job }: { readonly job: AiJob }) {
  const { queue } = useServices();
  return (
    <div className="flex flex-col gap-2" role="alert">
      <p className="font-semibold text-danger">{MESSAGES.quickEntry.failed}</p>
      <p className="text-muted">{inputPreview(job)}</p>
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={() => void queue.cancel(job.id)}>
          {MESSAGES.drafts.discard}
        </Button>
        <Button variant="secondary" onClick={() => void queue.retry(job.id)}>
          {MESSAGES.settings.retry}
        </Button>
      </div>
    </div>
  );
}

function Clarify({ job, question }: { readonly job: AiJob; readonly question: string }) {
  const { queue } = useServices();
  return (
    <div className="flex flex-col gap-2">
      <p className="font-semibold">{MESSAGES.quickEntry.clarify}</p>
      <p>{question}</p>
      <p className="text-muted">{inputPreview(job)}</p>
      <div className="flex justify-end">
        <Button variant="secondary" onClick={() => void queue.markReviewed(job.id)}>
          {MESSAGES.drafts.done}
        </Button>
      </div>
    </div>
  );
}

/** One AI job in any state: waiting, reading, failed, needs detail, or ready to check. */
export function JobCard({ job }: { readonly job: AiJob }) {
  const output = job.output;
  let body;
  if (job.state === 'queued') body = <Waiting job={job} />;
  else if (job.state === 'running')
    body = (
      <p className="text-muted" aria-live="polite">
        {MESSAGES.quickEntry.working}
      </p>
    );
  else if (job.state === 'failed') body = <Failed job={job} />;
  else if (output === null) body = null;
  else if (output.response.drafts.length === 0) body = <Clarify job={job} question={output.response.needsClarification ?? MESSAGES.quickEntry.empty} />;
  else body = <DraftReview job={job} output={output} />;
  return (
    <li className="rounded-2xl border border-border bg-surface p-4" data-testid="job" data-state={job.state}>
      {body}
    </li>
  );
}

/** Every job the trader still needs to see. */
export function JobList({ jobs }: { readonly jobs: readonly AiJob[] }) {
  if (jobs.length === 0) return null;
  return (
    <ul className="flex flex-col gap-3" aria-label={MESSAGES.drafts.readyToReview}>
      {jobs.map((job) => (
        <JobCard key={job.id} job={job} />
      ))}
    </ul>
  );
}
