import type { Services } from '@/app/services';

/** Builds the trader's data export (PRD Privacy: export as JSON). */
export async function buildExport(services: Pick<Services, 'journals' | 'customers'>): Promise<Blob> {
  const [journals, customers] = await Promise.all([services.journals.listAll(), services.customers.list()]);
  const data = { exportedAt: new Date().toISOString(), format: 'kpakam-export-v1', customers, journals };
  return new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
}

/** Saves a blob through a temporary link. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Deletes every document, the local queue, and the auth account (PRD Privacy). Irreversible. */
export async function deleteAccount(services: Pick<Services, 'user' | 'auth' | 'queue'>): Promise<void> {
  await services.user.deleteAllData();
  for (const job of await services.queue.list()) {
    if (job.state === 'queued' || job.state === 'failed') await services.queue.cancel(job.id);
  }
  await services.auth.deleteCurrentUser();
}
