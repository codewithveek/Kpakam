import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, serverTimestamp, setDoc, Timestamp, type DocumentData, type Firestore } from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach } from 'vitest';

export const PROJECT_ID = 'demo-kpakam';
export const ALICE = 'alice';
export const BOB = 'bob';

const RULES = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');

/**
 * Starts one rules test environment per test file and clears the emulator
 * before each test. Call at the top level of a test file.
 */
export function useTestEnv(): () => RulesTestEnvironment {
  let env: RulesTestEnvironment | undefined;
  beforeAll(async () => {
    env = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: { rules: RULES },
    });
  });
  beforeEach(async () => {
    await env?.clearFirestore();
  });
  afterAll(async () => {
    await env?.cleanup();
  });
  return () => {
    if (!env) throw new Error('Test environment is not ready');
    return env;
  };
}

/** Modular Firestore for a signed-in user (rules apply). */
export function dbAs(env: RulesTestEnvironment, uid: string): Firestore {
  return env.authenticatedContext(uid).firestore() as unknown as Firestore;
}

/** Modular Firestore for a signed-out client (rules apply). */
export function dbAnon(env: RulesTestEnvironment): Firestore {
  return env.unauthenticatedContext().firestore() as unknown as Firestore;
}

/** Writes documents with rules disabled, to set up a test. */
export async function seed(env: RulesTestEnvironment, docs: Record<string, DocumentData>): Promise<void> {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore() as unknown as Firestore;
    for (const [path, data] of Object.entries(docs)) {
      await setDoc(doc(db, path), data);
    }
  });
}

// --- ULIDs -----------------------------------------------------------------

const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

/** Deterministic, valid ULID for test number n. */
export function ulid(n: number): string {
  let tail = '';
  let x = n;
  for (let i = 0; i < 10; i += 1) {
    tail = CROCKFORD.charAt(x % 32) + tail;
    x = Math.floor(x / 32);
  }
  return '01JD0000000000' + tail.slice(-12).padStart(12, '0');
}

export const CUSTOMER_ID = ulid(1);
export const OTHER_CUSTOMER_ID = ulid(2);
export const JOURNAL_ID = ulid(100);
export const ORIGINAL_JOURNAL_ID = ulid(101);
export const EVENT_ID = 'evt-0001';

// --- Fixtures --------------------------------------------------------------

/** A timestamp in the past: never equal to request.time. */
export function pastTimestamp(): Timestamp {
  return Timestamp.fromMillis(Date.now() - 60 * 60 * 1000);
}

export function userDoc(overrides: DocumentData = {}): DocumentData {
  return {
    settings: { dataSaver: false, voiceLanguage: 'en', analyticsOptIn: false },
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    schemaVersion: 1,
    ...overrides,
  };
}

/** A user doc as stored (concrete timestamps), for seeding. */
export function storedUserDoc(overrides: DocumentData = {}): DocumentData {
  return userDoc({ createdAt: pastTimestamp(), updatedAt: pastTimestamp(), ...overrides });
}

export function customerDoc(id: string = CUSTOMER_ID, overrides: DocumentData = {}): DocumentData {
  return {
    id,
    name: 'Mama Tunde',
    nameKey: 'mama tunde',
    phone: '08031234567',
    balanceKobo: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    schemaVersion: 1,
    ...overrides,
  };
}

export function storedCustomerDoc(id: string = CUSTOMER_ID, overrides: DocumentData = {}): DocumentData {
  return customerDoc(id, { createdAt: pastTimestamp(), updatedAt: pastTimestamp(), ...overrides });
}

export function journalDoc(id: string = JOURNAL_ID, overrides: DocumentData = {}): DocumentData {
  return {
    id,
    idempotencyKey: `idem-${id}`,
    occurredAt: '2026-11-23T10:15:00.000+01:00',
    day: '2026-11-23',
    kind: 'sale',
    description: '3 bag rice',
    customerId: null,
    lines: [
      { account: 'cash', amountKobo: 1_500_000 },
      { account: 'sales', amountKobo: -1_500_000 },
    ],
    reverses: null,
    source: { inference: 'manual', reason: 'TYPED', promptVersion: null },
    createdAt: serverTimestamp(),
    schemaVersion: 1,
    ...overrides,
  };
}

export function storedJournalDoc(id: string = ORIGINAL_JOURNAL_ID, overrides: DocumentData = {}): DocumentData {
  return journalDoc(id, { createdAt: pastTimestamp(), ...overrides });
}

export function aiEventDoc(id: string = EVENT_ID, overrides: DocumentData = {}): DocumentData {
  return {
    id,
    task: 'parse_text',
    target: 'device',
    reason: 'DEFAULT_DEVICE',
    source: 'on_device',
    latencyMs: 820,
    valid: true,
    attempts: 1,
    escalated: false,
    queuedMs: null,
    promptVersion: 'parse-v1',
    createdAt: serverTimestamp(),
    schemaVersion: 1,
    ...overrides,
  };
}

export function storedAiEventDoc(id: string = EVENT_ID, overrides: DocumentData = {}): DocumentData {
  return aiEventDoc(id, { createdAt: pastTimestamp(), ...overrides });
}

/** Copy of data without one key. */
export function without(data: DocumentData, key: string): DocumentData {
  const copy = { ...data };
  delete copy[key];
  return copy;
}

export const userPath = (uid: string) => `users/${uid}`;
export const customerPath = (uid: string, id: string = CUSTOMER_ID) => `users/${uid}/customers/${id}`;
export const journalPath = (uid: string, id: string = JOURNAL_ID) => `users/${uid}/journals/${id}`;
export const aiEventPath = (uid: string, id: string = EVENT_ID) => `users/${uid}/aiEvents/${id}`;
