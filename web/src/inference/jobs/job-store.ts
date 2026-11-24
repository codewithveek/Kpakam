import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type { AiJob } from './job';
import { transition, type JobState } from './job-state';

interface QueueDb extends DBSchema {
  jobs: {
    key: string;
    value: AiJob;
    indexes: { byKey: string; byState: JobState };
  };
}

/** Durable storage for AI jobs. One implementation per platform; tests use the same one with fake-indexeddb. */
export interface JobStore {
  /** Adds a job unless one with the same idempotency key exists. Returns the stored job. */
  add(job: AiJob): Promise<AiJob>;
  get(id: string): Promise<AiJob | undefined>;
  list(): Promise<AiJob[]>;
  /** Applies a change; a state change is checked against the state machine. */
  update(id: string, change: Partial<Omit<AiJob, 'id' | 'idempotencyKey'>>): Promise<AiJob>;
  remove(id: string): Promise<void>;
  clear(): Promise<void>;
}

const DB_NAME = 'kpakam-queue';
const DB_VERSION = 1;

function openQueueDb(name: string): Promise<IDBPDatabase<QueueDb>> {
  return openDB<QueueDb>(name, DB_VERSION, {
    upgrade(db) {
      const store = db.createObjectStore('jobs', { keyPath: 'id' });
      store.createIndex('byKey', 'idempotencyKey', { unique: true });
      store.createIndex('byState', 'state');
    },
  });
}

/** IndexedDB job store (via `idb`). */
export function createIndexedDbJobStore(name: string = DB_NAME): JobStore {
  const dbPromise = openQueueDb(name);
  return {
    async add(job) {
      const db = await dbPromise;
      const tx = db.transaction('jobs', 'readwrite');
      const existing = await tx.store.index('byKey').get(job.idempotencyKey);
      if (existing === undefined) await tx.store.add(job);
      await tx.done;
      return existing ?? job;
    },
    async get(id) {
      return (await dbPromise).get('jobs', id);
    },
    async list() {
      const jobs = await (await dbPromise).getAll('jobs');
      return jobs.sort((a, b) => a.id.localeCompare(b.id));
    },
    async update(id, change) {
      const db = await dbPromise;
      const tx = db.transaction('jobs', 'readwrite');
      const current = await tx.store.get(id);
      if (current === undefined) throw new Error(`AI job not found: ${id}`);
      const state = change.state === undefined ? current.state : transition(current.state, change.state);
      const next: AiJob = { ...current, ...change, state, updatedAt: new Date().toISOString() };
      await tx.store.put(next);
      await tx.done;
      return next;
    },
    async remove(id) {
      await (await dbPromise).delete('jobs', id);
    },
    async clear() {
      await (await dbPromise).clear('jobs');
    },
  };
}
