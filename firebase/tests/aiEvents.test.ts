import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, Timestamp, updateDoc, where, type DocumentData } from 'firebase/firestore';
import { describe, it } from 'vitest';
import { ALICE, aiEventDoc, aiEventPath, BOB, dbAnon, dbAs, EVENT_ID, pastTimestamp, seed, storedAiEventDoc, useTestEnv, without } from './helpers';

const env = useTestEnv();

const AI_EVENT_KEYS = [
  'id',
  'task',
  'target',
  'reason',
  'source',
  'latencyMs',
  'valid',
  'attempts',
  'escalated',
  'queuedMs',
  'promptVersion',
  'createdAt',
  'schemaVersion',
];

function createAsAlice(data: DocumentData, id: string = EVENT_ID) {
  return setDoc(doc(dbAs(env(), ALICE), aiEventPath(ALICE, id)), data);
}

describe('aiEvents create', () => {
  it('allows a valid event', async () => {
    await assertSucceeds(createAsAlice(aiEventDoc()));
  });

  it('allows a queued cloud event with queuedMs and no promptVersion', async () => {
    await assertSucceeds(
      createAsAlice(
        aiEventDoc(EVENT_ID, {
          task: 'parse_audio',
          target: 'queue',
          reason: 'OFFLINE',
          source: 'none',
          latencyMs: 0,
          valid: false,
          attempts: 0,
          escalated: true,
          queuedMs: 120_000,
          promptVersion: null,
        }),
      ),
    );
  });

  it('allows the boundary values latencyMs 600000 and attempts 5', async () => {
    await assertSucceeds(createAsAlice(aiEventDoc(EVENT_ID, { latencyMs: 600_000, attempts: 5 })));
  });

  it("denies writing another user's events", async () => {
    await assertFails(setDoc(doc(dbAs(env(), BOB), aiEventPath(ALICE)), aiEventDoc()));
  });

  it('denies an unauthenticated create', async () => {
    await assertFails(setDoc(doc(dbAnon(env()), aiEventPath(ALICE)), aiEventDoc()));
  });

  it('denies an extra free-text field (no trader text allowed)', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { text: 'I sell 3 bag rice to Mama Tunde' })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { error: 'model said: ...' })));
  });

  for (const key of AI_EVENT_KEYS) {
    it(`denies a missing ${key}`, async () => {
      await assertFails(createAsAlice(without(aiEventDoc(), key)));
    });
  }

  it('denies id that does not match the document ID', async () => {
    await assertFails(createAsAlice(aiEventDoc('evt-other')));
  });

  it('denies createdAt that is not request.time', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { createdAt: pastTimestamp() })));
  });

  it('denies an unknown schemaVersion', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { schemaVersion: 2 })));
  });

  it('denies unknown enum values', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { task: 'chat' })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { target: 'server' })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { source: 'manual' })));
  });

  it('denies a reason that is empty or over 40 chars', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { reason: '' })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { reason: 'r'.repeat(41) })));
  });

  it('denies a promptVersion that is empty or over 40 chars', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { promptVersion: '' })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { promptVersion: 'p'.repeat(41) })));
  });

  it('denies latencyMs out of range or not an int', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { latencyMs: -1 })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { latencyMs: 600_001 })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { latencyMs: 12.5 })));
  });

  it('denies attempts out of range or not an int', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { attempts: -1 })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { attempts: 6 })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { attempts: 1.5 })));
  });

  it('denies a negative or non-int queuedMs', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { queuedMs: -1 })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { queuedMs: 1.5 })));
  });

  it('denies non-bool valid and escalated', async () => {
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { valid: 'true' })));
    await assertFails(createAsAlice(aiEventDoc(EVENT_ID, { escalated: 1 })));
  });
});

describe('aiEvents read and query', () => {
  it('allows the owner to get and to query by createdAt', async () => {
    await seed(env(), { [aiEventPath(ALICE)]: storedAiEventDoc() });
    const db = dbAs(env(), ALICE);
    await assertSucceeds(getDoc(doc(db, aiEventPath(ALICE))));
    const since = Timestamp.fromMillis(Date.now() - 7 * 24 * 60 * 60 * 1000);
    await assertSucceeds(getDocs(query(collection(db, `users/${ALICE}/aiEvents`), where('createdAt', '>=', since))));
  });

  it("denies reading another user's events", async () => {
    await seed(env(), { [aiEventPath(ALICE)]: storedAiEventDoc() });
    const db = dbAs(env(), BOB);
    await assertFails(getDoc(doc(db, aiEventPath(ALICE))));
    await assertFails(getDocs(collection(db, `users/${ALICE}/aiEvents`)));
  });

  it('denies an unauthenticated read', async () => {
    await seed(env(), { [aiEventPath(ALICE)]: storedAiEventDoc() });
    await assertFails(getDoc(doc(dbAnon(env()), aiEventPath(ALICE))));
  });
});

describe('aiEvents are append-only', () => {
  it('denies update', async () => {
    await seed(env(), { [aiEventPath(ALICE)]: storedAiEventDoc() });
    await assertFails(updateDoc(doc(dbAs(env(), ALICE), aiEventPath(ALICE)), { valid: false }));
  });

  it('denies delete when deletion is not requested', async () => {
    await seed(env(), { [aiEventPath(ALICE)]: storedAiEventDoc() });
    await assertFails(deleteDoc(doc(dbAs(env(), ALICE), aiEventPath(ALICE))));
  });
});
