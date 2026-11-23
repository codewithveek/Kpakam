import type { FirebaseApp } from 'firebase/app';
import { createContext, useContext } from 'react';
import type { CustomerRepository, JournalRepository } from '@/features/ledger/repository/journal-repository';
import type { LedgerService } from '@/features/ledger/service/ledger-service';
import type { UserRepository } from '@/features/settings/repository/user-repository';
import type { RoutingStatsSource } from '@/inference/ai-event-repository';
import type { InferenceClient } from '@/inference/clients/inference-client';
import type { JobQueue } from '@/inference/jobs/job-queue';
import type { TextTaskService } from '@/inference/text-task-service';
import type { AuthAdapter } from '@/platform/firebase/auth-adapter';
import type { RemoteConfigAdapter } from '@/platform/firebase/remote-config-adapter';
import type { NetworkMonitor } from '@/platform/network';
import type { SpeechAdapter } from '@/platform/speech';

/** Everything the UI needs, behind our own interfaces. Created once per signed-in trader. */
export interface Services {
  readonly app: FirebaseApp;
  readonly ledger: LedgerService;
  readonly journals: JournalRepository;
  readonly customers: CustomerRepository;
  readonly queue: JobQueue;
  readonly inference: InferenceClient;
  readonly textTasks: TextTaskService;
  readonly user: UserRepository;
  readonly auth: AuthAdapter;
  readonly stats: RoutingStatsSource;
  readonly speech: SpeechAdapter;
  readonly network: NetworkMonitor;
  readonly remoteConfig: RemoteConfigAdapter;
}

export const ServicesContext = createContext<Services | null>(null);

/**
 * Returns the app services.
 * @throws Error when used outside `ServicesContext` (a wiring bug).
 */
export function useServices(): Services {
  const services = useContext(ServicesContext);
  if (services === null) throw new Error('useServices must be used inside ServicesContext');
  return services;
}
