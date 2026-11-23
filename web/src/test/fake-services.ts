import type { Services } from '@/app/services';
import { createLedgerService } from '@/features/ledger/service/ledger-service';
import { summariseEvents } from '@/inference/ai-event-repository';
import type { AiEventRecord } from '@/inference/ai-event';
import { createScriptedInferenceClient, type ScriptedClientOptions } from '@/inference/clients/scripted-inference-client';
import { createIndexedDbJobStore } from '@/inference/jobs/job-store';
import { createJobQueue } from '@/inference/jobs/job-queue';
import { createRateLimiter } from '@/inference/jobs/rate-limiter';
import { createParseService } from '@/inference/parse-service';
import { PROMPTS } from '@/inference/prompt-library';
import type { EnvironmentSnapshot } from '@/inference/signals';
import { createTextTaskService } from '@/inference/text-task-service';
import { DEFAULT_CONFIG } from '@/platform/firebase/remote-config-adapter';
import { createInMemoryLedger } from './in-memory-ledger';

let dbCount = 0;

/** Full `Services` for component tests: in-memory ledger, real queue and parse service, scripted model. */
export function createFakeServices(options: { script?: ScriptedClientOptions; env?: Partial<EnvironmentSnapshot> } = {}) {
  const ledger = createInMemoryLedger();
  const events: AiEventRecord[] = [];
  const { client, calls } = createScriptedInferenceClient(options.script ?? {});
  const env: EnvironmentSnapshot = {
    online: true,
    cloudEnabled: true,
    dataSaver: false,
    battery: null,
    deviceModel: 'available',
    localSpeech: 'unavailable',
    voiceLanguage: 'en',
    config: { maxDeviceChars: 280, languageThreshold: 0.35, minBattery: 0.15 },
    ...options.env,
  };
  const signals = { snapshot: () => Promise.resolve(env) };
  const sink = { record: (e: AiEventRecord) => void events.push(e) };
  const parser = createParseService({ client, signals, prompts: PROMPTS, events: sink, rateLimiter: createRateLimiter(() => 10) });
  dbCount += 1;
  const services = {
    app: {} as Services['app'],
    ledger: createLedgerService({ journals: ledger.journalRepo, customers: ledger.customerRepo }),
    journals: ledger.journalRepo,
    customers: ledger.customerRepo,
    queue: createJobQueue({ store: createIndexedDbJobStore(`component-${dbCount}`), parser, toBase64: () => Promise.resolve('AA==') }),
    inference: client,
    textTasks: createTextTaskService({ client, signals, events: sink, prompts: PROMPTS }),
    user: { watchSettings: () => () => undefined, saveSettings: () => Promise.resolve(), deleteAllData: () => Promise.resolve() },
    auth: {
      ensureSignedIn: () => Promise.reject(new Error('unused')),
      linkGoogle: () => Promise.reject(new Error('unused')),
      current: () => null,
      deleteCurrentUser: () => Promise.resolve(),
    },
    stats: { watchStats: (_since: Date, onChange: (s: ReturnType<typeof summariseEvents>) => void) => (onChange(summariseEvents(events)), () => undefined) },
    speech: {
      localState: () => Promise.resolve('unavailable' as const),
      cachedState: () => 'unavailable' as const,
      installLocal: () => Promise.resolve(),
      startLocal: () => null,
    },
    network: { isOnline: () => env.online, saveDataHint: () => false, reportServerReachable: () => undefined, subscribe: () => () => undefined },
    remoteConfig: { values: () => DEFAULT_CONFIG, refresh: () => Promise.resolve() },
  } satisfies Services;
  return { services, ledger, calls, events };
}
