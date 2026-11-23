import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, deleteField, doc, getDoc, getDocs, increment, orderBy, query, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { describe, it } from 'vitest';
import {
  ALICE,
  BOB,
  CUSTOMER_ID,
  customerDoc,
  customerPath,
  dbAnon,
  dbAs,
  OTHER_CUSTOMER_ID,
  pastTimestamp,
  seed,
  storedCustomerDoc,
  useTestEnv,
  without,
} from './helpers';

const env = useTestEnv();

const CUSTOMER_KEYS = ['id', 'name', 'nameKey', 'phone', 'balanceKobo', 'createdAt', 'updatedAt', 'schemaVersion'];

describe('customers create', () => {
  it('allows the owner to create a valid customer', async () => {
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc()));
  });

  it('allows a null phone and an empty phone', async () => {
    const db = dbAs(env(), ALICE);
    await assertSucceeds(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { phone: null })));
    await assertSucceeds(setDoc(doc(db, customerPath(ALICE, OTHER_CUSTOMER_ID)), customerDoc(OTHER_CUSTOMER_ID, { phone: '' })));
  });

  it('allows a negative balance (trader owes customer change)', async () => {
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { balanceKobo: -500 })));
  });

  it('allows balanceKobo written with increment() on create', async () => {
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { balanceKobo: increment(250_000) })));
  });

  it("denies creating a customer in another user's ledger", async () => {
    await assertFails(setDoc(doc(dbAs(env(), BOB), customerPath(ALICE)), customerDoc()));
  });

  it('denies an unauthenticated create', async () => {
    await assertFails(setDoc(doc(dbAnon(env()), customerPath(ALICE)), customerDoc()));
  });

  it('denies id that does not match the document ID', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(OTHER_CUSTOMER_ID)));
  });

  it('denies a document ID that is not a ULID', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE, 'not-a-ulid')), customerDoc('not-a-ulid')));
  });

  it('denies createdAt that is not request.time', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { createdAt: pastTimestamp() })));
  });

  it('denies updatedAt that is not request.time', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { updatedAt: pastTimestamp() })));
  });

  for (const key of CUSTOMER_KEYS) {
    it(`denies a missing ${key}`, async () => {
      await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), without(customerDoc(), key)));
    });
  }

  it('denies an extra field', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { notes: 'x' })));
  });

  it('denies an unknown schemaVersion', async () => {
    await assertFails(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { schemaVersion: 2 })));
  });

  it('denies an empty name and a name over 60 chars', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { name: '' })));
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { name: 'a'.repeat(61) })));
  });

  it('allows a 60-char name', async () => {
    await assertSucceeds(setDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), customerDoc(CUSTOMER_ID, { name: 'a'.repeat(60), nameKey: 'a'.repeat(60) })));
  });

  it('denies an empty nameKey and a nameKey over 60 chars', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { nameKey: '' })));
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { nameKey: 'a'.repeat(61) })));
  });

  it('denies a phone over 20 chars and a non-string phone', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { phone: '0'.repeat(21) })));
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { phone: 8031234567 })));
  });

  it('denies a non-integer balanceKobo', async () => {
    const db = dbAs(env(), ALICE);
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { balanceKobo: 1.5 })));
    await assertFails(setDoc(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { balanceKobo: '100' })));
  });
});

describe('customers read and query', () => {
  it('allows the owner to read and to query by balance', async () => {
    await seed(env(), {
      [customerPath(ALICE)]: storedCustomerDoc(CUSTOMER_ID, { balanceKobo: 900 }),
      [customerPath(ALICE, OTHER_CUSTOMER_ID)]: storedCustomerDoc(OTHER_CUSTOMER_ID, { balanceKobo: 100 }),
    });
    const db = dbAs(env(), ALICE);
    await assertSucceeds(getDoc(doc(db, customerPath(ALICE))));
    await assertSucceeds(getDocs(query(collection(db, `users/${ALICE}/customers`), orderBy('balanceKobo', 'desc'))));
  });

  it("denies reading or querying another user's customers", async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    const db = dbAs(env(), BOB);
    await assertFails(getDoc(doc(db, customerPath(ALICE))));
    await assertFails(getDocs(query(collection(db, `users/${ALICE}/customers`), orderBy('balanceKobo', 'desc'))));
  });

  it('denies an unauthenticated read', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(getDoc(doc(dbAnon(env()), customerPath(ALICE))));
  });
});

describe('customers update', () => {
  it('allows renaming with updatedAt == request.time', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertSucceeds(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        name: 'Iya Tunde',
        nameKey: 'iya tunde',
        phone: null,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('allows a balance increment()', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertSucceeds(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        balanceKobo: increment(-120_000),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies an update without updatedAt == request.time', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), { balanceKobo: increment(100) }));
  });

  it('denies changing id', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        id: OTHER_CUSTOMER_ID,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies changing createdAt', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies changing schemaVersion', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        schemaVersion: 2,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies adding a field', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        notes: 'owes since March',
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies removing a field', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), ALICE), customerPath(ALICE)), {
        phone: deleteField(),
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it('denies an invalid value on update (update bypass)', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    const db = dbAs(env(), ALICE);
    await assertFails(updateDoc(doc(db, customerPath(ALICE)), { name: 'a'.repeat(61), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, customerPath(ALICE)), { balanceKobo: 10.5, updatedAt: serverTimestamp() }));
  });

  it("denies updating another user's customer", async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(
      updateDoc(doc(dbAs(env(), BOB), customerPath(ALICE)), {
        balanceKobo: increment(100),
        updatedAt: serverTimestamp(),
      }),
    );
  });
});

describe('customers delete', () => {
  it('denies delete when deletion is not requested', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertFails(deleteDoc(doc(dbAs(env(), ALICE), customerPath(ALICE))));
  });
});
