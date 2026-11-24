import { useEffect, useState } from 'react';
import { useServices } from '@/app/services';
import type { AiJob } from '@/inference/jobs/job';
import { visibleJobs } from '@/inference/jobs/job-queue';

/** Live list of AI jobs the trader still needs to see (newest first). */
export function useJobs(): readonly AiJob[] {
  const { queue } = useServices();
  const [jobs, setJobs] = useState<readonly AiJob[]>([]);
  useEffect(() => queue.subscribe((all) => setJobs(visibleJobs(all).reverse())), [queue]);
  return jobs;
}
