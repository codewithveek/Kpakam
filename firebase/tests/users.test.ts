import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, deleteField, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { describe, it } from 'vitest';
import { ALICE, BOB, dbAnon, dbAs, pastTimestamp, seed, storedUserDoc, useTestEnv, userDoc, userPath, without } from './helpers';

const env = useTestEnv();

describe('users/{uid} create', () => {
  it('allows the owner to create a valid user doc', async () => {
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), userDoc()));
  });

  it('allows voiceLanguage auto (the default: language worked out from the voice)', async () => {
    const data = userDoc({
      settings: { dataSaver: false, voiceLanguage: 'auto', analyticsOptIn: false },
    });
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), data));
  });

  it('allows voiceLanguage pcm_yo', async () => {
    const data = userDoc({
      settings: { dataSaver: true, voiceLanguage: 'pcm_yo', analyticsOptIn: true },
    });
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), data));
  });

  it('denies creating another user doc', async () => {
    await assertFails(setDoc(doc(dbAs(env(), BOB), userPath(ALICE)), userDoc()));
  });

  it('denies an unauthenticated create', async () => {
    await assertFails(setDoc(doc(dbAnon(env()), userPath(ALICE)), userDoc()));
  });

  it('denies createdAt that is not request.time', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), userDoc({ createdAt: pastTimestamp() })));
  });

  it('denies updatedAt that is not request.time', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), userDoc({ updatedAt: pastTimestamp() })));
  });

  for (const key of ['settings', 'createdAt', 'updatedAt', 'schemaVersion']) {
    it(`denies a missing ${key}`, async () => {
      await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), without(userDoc(), key)));
    });
  }

  it('denies an extra field', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), userDoc({ role: 'admin' })));
  });

  it('denies an unknown schemaVersion', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), userDoc({ schemaVersion: 2 })));
  });

  it('denies an unknown voiceLanguage', async () => {
    const data = userDoc({
      settings: { dataSaver: false, voiceLanguage: 'fr', analyticsOptIn: false },
    });
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), data));
  });

  it('denies an extra settings key', async () => {
    const data = userDoc({
      settings: { dataSaver: false, voiceLanguage: 'en', analyticsOptIn: false, theme: 'dark' },
    });
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), data));
  });

  it('denies a missing settings key', async () => {
    const data = userDoc({ settings: { dataSaver: false, voiceLanguage: 'en' } });
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), data));
  });

  it('denies a non-bool dataSaver', async () => {
    const data = userDoc({
      settings: { dataSaver: 'yes', voiceLanguage: 'en', analyticsOptIn: false },
    });
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), data));
  });

  it('allows deletionRequestedAt on create only as request.time', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, userPath(ALICE)), userDoc({ deletionRequestedAt: pastTimestamp() })));
    await assertSucceeds(setDoc(doc(db, userPath(ALICE)), userDoc({ deletionRequestedAt: serverTimestamp() })));
  });
});

describe('users/{uid} read', () => {
  it('allows the owner to read', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertSucceeds(getDoc(doc(dbAs(env(), ALICE), userPath(ALICE))));
  });

  it('denies another user', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(getDoc(doc(dbAs(env(), BOB), userPath(ALICE))));
  });

  it('denies an unauthenticated read', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(getDoc(doc(dbAnon(env()), userPath(ALICE))));
  });
});

describe('users/{uid} update', () => {
  it('allows the owner to change settings', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertSucceeds(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        'settings.dataSaver': true,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies another user', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), BOB), userPath(ALICE)), {
        'settings.dataSaver': true,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies an update without updatedAt == request.time', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), { 'settings.dataSaver': true }));
  });

  it('denies changing createdAt', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies changing schemaVersion', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        schemaVersion: 2,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies adding an extra field', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        isAdmin: true,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies removing a required field', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        settings: deleteField(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('allows setting deletionRequestedAt to request.time', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertSucceeds(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        deletionRequestedAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies a backdated deletionRequestedAt', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        deletionRequestedAt: pastTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('allows other updates once deletion is requested, keeping deletionRequestedAt', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertSucceeds(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        'settings.analyticsOptIn': true,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies removing deletionRequestedAt', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        deletionRequestedAt: deleteField(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies changing deletionRequestedAt', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), {
        deletionRequestedAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies overwriting the doc without deletionRequestedAt via set()', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertFails(setDoc(doc(dbAs(env(), ALICE), userPath(ALICE)), userDoc()));
  });
});

describe('users/{uid} delete', () => {
  it('denies delete before deletion is requested', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc() });
    await assertFails(deleteDoc(doc(dbAs(env(), ALICE), userPath(ALICE))));
  });

  it('allows delete after deletion is requested', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertSucceeds(deleteDoc(doc(dbAs(env(), ALICE), userPath(ALICE))));
  });

  it('denies another user even after deletion is requested', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertFails(deleteDoc(doc(dbAs(env(), BOB), userPath(ALICE))));
  });

  it('denies an unauthenticated delete', async () => {
    await seed(env(), { [userPath(ALICE)]: storedUserDoc({ deletionRequestedAt: pastTimestamp() }) });
    await assertFails(deleteDoc(doc(dbAnon(env()), userPath(ALICE))));
  });
});
