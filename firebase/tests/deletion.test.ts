import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch, type DocumentData, type Firestore } from 'firebase/firestore';
import { describe, it } from 'vitest';
import {
  ALICE,
  aiEventPath,
  BOB,
  customerPath,
  dbAnon,
  dbAs,
  journalPath,
  pastTimestamp,
  seed,
  storedAiEventDoc,
  storedCustomerDoc,
  storedJournalDoc,
  storedUserDoc,
  ulid,
  useTestEnv,
  userPath,
} from './helpers';

const env = useTestEnv();

/** Seeds Alice's ledger: user doc, n customers, n journals, n aiEvents. */
async function seedLedger(n: number, user: DocumentData = storedUserDoc()) {
  const docs: Record<string, DocumentData> = { [userPath(ALICE)]: user };
  const paths: string[] = [];
  for (let i = 0; i < n; i += 1) {
    const cid = ulid(1000 + i);
    const jid = ulid(2000 + i);
    const eid = `evt-${i}`;
    docs[customerPath(ALICE, cid)] = storedCustomerDoc(cid);
    docs[journalPath(ALICE, jid)] = storedJournalDoc(jid);
    docs[aiEventPath(ALICE, eid)] = storedAiEventDoc(eid);
    paths.push(customerPath(ALICE, cid), journalPath(ALICE, jid), aiEventPath(ALICE, eid));
  }
  await seed(env(), docs);
  return paths;
}

describe('account deletion path', () => {
  it('runs end to end: request, delete subcollections, delete user doc', async () => {
    const paths = await seedLedger(2);
    const db = dbAs(env(), ALICE);

    // Step 1: request deletion (committed before any delete).
    await assertSucceeds(
      updateDoc(doc(db, userPath(ALICE)), {
        deletionRequestedAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );

    // Step 2 + 3: delete every subcollection doc, then the user doc, in one batch.
    const batch = writeBatch(db);
    for (const p of paths) batch.delete(doc(db, p));
    batch.delete(doc(db, userPath(ALICE)));
    await assertSucceeds(batch.commit());

    const after = await getDoc(doc(db, userPath(ALICE)));
    if (after.exists()) throw new Error('user doc still exists');
  });

  it('allows a large delete batch (60 docs, one user-doc lookup each)', async () => {
    const paths = await seedLedger(20, storedUserDoc({ deletionRequestedAt: pastTimestamp() }));
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    for (const p of paths) batch.delete(doc(db, p));
    await assertSucceeds(batch.commit());
  });

  it('denies subcollection deletes before deletion is requested', async () => {
    const paths = await seedLedger(1);
    const db = dbAs(env(), ALICE);
    for (const p of paths) await assertFails(deleteDoc(doc(db, p)));
  });

  it('denies subcollection deletes requested in the same batch (must be committed first)', async () => {
    const paths = await seedLedger(1);
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    batch.update(doc(db, userPath(ALICE)), {
      deletionRequestedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    for (const p of paths) batch.delete(doc(db, p));
    await assertFails(batch.commit());
  });

  it('denies subcollection deletes when the user doc is missing', async () => {
    const paths = await seedLedger(1);
    await env().withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore() as unknown as Firestore, userPath(ALICE)));
    });
    const db = dbAs(env(), ALICE);
    for (const p of paths) await assertFails(deleteDoc(doc(db, p)));
  });

  it('allows subcollection deletes after deletion is requested', async () => {
    const paths = await seedLedger(1, storedUserDoc({ deletionRequestedAt: pastTimestamp() }));
    const db = dbAs(env(), ALICE);
    for (const p of paths) await assertSucceeds(deleteDoc(doc(db, p)));
  });

  it('denies another user deleting the ledger even after deletion is requested', async () => {
    const paths = await seedLedger(1, storedUserDoc({ deletionRequestedAt: pastTimestamp() }));
    const db = dbAs(env(), BOB);
    for (const p of paths) await assertFails(deleteDoc(doc(db, p)));
    await assertFails(deleteDoc(doc(db, userPath(ALICE))));
  });

  it('denies unauthenticated deletes even after deletion is requested', async () => {
    const paths = await seedLedger(1, storedUserDoc({ deletionRequestedAt: pastTimestamp() }));
    const db = dbAnon(env());
    for (const p of paths) await assertFails(deleteDoc(doc(db, p)));
  });

  it('denies undoing the request after it is set', async () => {
    await seedLedger(0, storedUserDoc({ deletionRequestedAt: pastTimestamp() }));
    const db = dbAs(env(), ALICE);
    await assertFails(
      setDoc(doc(db, userPath(ALICE)), {
        settings: { dataSaver: false, voiceLanguage: 'en', analyticsOptIn: false },
        createdAt: pastTimestamp(),
        updatedAt: serverTimestamp(),
        schemaVersion: 1,
      }),
    );
  });
});

describe('default deny', () => {
  it('denies unknown top-level collections, even for a signed-in user', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, 'admin/config'), { open: true }));
    await assertFails(getDoc(doc(db, 'admin/config')));
    await assertFails(setDoc(doc(db, `profiles/${ALICE}`), { name: 'x' }));
  });

  it('denies unknown subcollections under the own user doc', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, `users/${ALICE}/notes/n1`), { text: 'hello' }));
    await assertFails(getDoc(doc(db, `users/${ALICE}/notes/n1`)));
  });

  it('denies deeper nesting under a journal', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, `users/${ALICE}/journals/${ulid(5)}/attachments/a1`), { x: 1 }));
  });

  it('denies every unauthenticated access to user data', async () => {
    await seedLedger(1);
    const db = dbAnon(env());
    await assertFails(getDoc(doc(db, userPath(ALICE))));
    await assertFails(getDoc(doc(db, customerPath(ALICE, ulid(1000)))));
    await assertFails(getDoc(doc(db, journalPath(ALICE, ulid(2000)))));
    await assertFails(getDoc(doc(db, aiEventPath(ALICE, 'evt-0'))));
  });
});
