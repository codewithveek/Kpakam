import { createFirestoreCustomerRepository } from '@/features/ledger/repository/firestore-customer-repository';
import { createFirestoreJournalRepository } from '@/features/ledger/repository/firestore-journal-repository';
import { createLedgerService } from '@/features/ledger/service/ledger-service';
import { createFirestoreUserRepository } from '@/features/settings/repository/user-repository';
import { createFirestoreAiEvents } from '@/inference/ai-event-repository';
import type { InferenceClient } from '@/inference/clients/inference-client';
import { createIndexedDbJobStore } from '@/inference/jobs/job-store';
import { createJobQueue } from '@/inference/jobs/job-queue';
import { createRateLimiter } from '@/inference/jobs/rate-limiter';
import { createParseService } from '@/inference/parse-service';
import { PROMPTS } from '@/inference/prompt-library';
import type { SignalSource } from '@/inference/signals';
import { createTextTaskService } from '@/inference/text-task-service';
import { readBattery } from '@/platform/battery';
import type { AuthAdapter } from '@/platform/firebase/auth-adapter';
import type { FirebaseHandles } from '@/platform/firebase/app';
import type { RemoteConfigAdapter } from '@/platform/firebase/remote-config-adapter';
import { blobToBase64 } from '@/platform/image';
import type { NetworkMonitor } from '@/platform/network';
import type { SpeechAdapter } from '@/platform/speech';
import { useAppStore } from './app-store';
import type { Services } from './services';

export interface ServiceDeps {
  readonly handles: FirebaseHandles;
  readonly uid: string;
  readonly auth: AuthAdapter;
  readonly inference: InferenceClient;
  readonly network: NetworkMonitor;
  readonly remoteConfig: RemoteConfigAdapter;
  readonly speech: SpeechAdapter;
}

function createSignalSource(deps: ServiceDeps): SignalSource {
  return {
    async snapshot() {
      const config = deps.remoteConfig.values();
      const [battery, deviceModel] = await Promise.all([readBattery(), deps.inference.deviceModelState()]);
      const localSpeech = deps.speech.cachedState();
      const { settings } = useAppStore.getState();
      useAppStore.getState().setDeviceModel(deviceModel);
      return {
        online: deps.network.isOnline(),
        cloudEnabled: config.cloudEnabled,
        dataSaver: settings.dataSaver,
        battery,
        deviceModel,
        localSpeech,
        voiceLanguage: settings.voiceLanguage,
        config: { maxDeviceChars: config.maxDeviceChars, languageThreshold: config.languageThreshold, minBattery: config.minBattery },
      };
    },
  };
}

/** Wires adapters, repositories, and services together. */
export function createServices(deps: ServiceDeps): Services {
  const ctx = { db: deps.handles.db, uid: deps.uid, onServerSignal: (reachable: boolean) => deps.network.reportServerReachable(reachable) };
  const journals = createFirestoreJournalRepository(ctx);
  const customers = createFirestoreCustomerRepository(ctx);
  const events = createFirestoreAiEvents(deps.handles.db, deps.uid);
  const signals = createSignalSource(deps);
  const rateLimiter = createRateLimiter(() => deps.remoteConfig.values().cloudRateLimitPerMinute);
  const parser = createParseService({ client: deps.inference, signals, prompts: PROMPTS, events, rateLimiter });
  return {
    app: deps.handles.app,
    ledger: createLedgerService({ journals, customers }),
    journals,
    customers,
    queue: createJobQueue({ store: createIndexedDbJobStore(`kpakam-queue-${deps.uid}`), parser, toBase64: blobToBase64 }),
    inference: deps.inference,
    textTasks: createTextTaskService({ client: deps.inference, signals, events, prompts: PROMPTS }),
    user: createFirestoreUserRepository(deps.handles.db, deps.uid),
    auth: deps.auth,
    stats: events,
    speech: deps.speech,
    network: deps.network,
    remoteConfig: deps.remoteConfig,
  };
}
