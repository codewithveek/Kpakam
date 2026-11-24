import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type DocumentData,
} from 'firebase/firestore';
import { describe, it } from 'vitest';
import {
  ALICE,
  BOB,
  CUSTOMER_ID,
  customerDoc,
  customerPath,
  dbAnon,
  dbAs,
  JOURNAL_ID,
  journalDoc,
  journalPath,
  ORIGINAL_JOURNAL_ID,
  OTHER_CUSTOMER_ID,
  pastTimestamp,
  seed,
  storedCustomerDoc,
  storedJournalDoc,
  ulid,
  useTestEnv,
  without,
} from './helpers';

const env = useTestEnv();

const JOURNAL_KEYS = [
  'id',
  'idempotencyKey',
  'occurredAt',
  'day',
  'kind',
  'description',
  'customerId',
  'lines',
  'reverses',
  'source',
  'createdAt',
  'schemaVersion',
];

const RECEIVABLE = `receivable:${CUSTOMER_ID}`;

/** Create one journal as Alice (no batch). */
function createAsAlice(data: DocumentData, id: string = JOURNAL_ID) {
  return setDoc(doc(dbAs(env(), ALICE), journalPath(ALICE, id)), data);
}

/** A balanced list of n lines (n >= 2). */
function balancedLines(n: number) {
  const lines = [];
  for (let i = 0; i < n - 1; i += 1) lines.push({ account: 'cash', amountKobo: 100 });
  lines.push({ account: 'sales', amountKobo: -100 * (n - 1) });
  return lines;
}

describe('journals create: valid journals', () => {
  it('allows a cash sale with no customer', async () => {
    await assertSucceeds(createAsAlice(journalDoc()));
  });

  it('allows an expense', async () => {
    await assertSucceeds(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          kind: 'expense',
          lines: [
            { account: 'expenses', amountKobo: 50_000 },
            { account: 'cash', amountKobo: -50_000 },
          ],
        }),
      ),
    );
  });

  it('allows the maximum of 10 lines', async () => {
    await assertSucceeds(createAsAlice(journalDoc(JOURNAL_ID, { lines: balancedLines(10) })));
  });

  it('allows a 3-line credit sale that creates the customer in the same batch', async () => {
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    batch.set(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { balanceKobo: increment(1_000_000) }));
    batch.set(
      doc(db, journalPath(ALICE)),
      journalDoc(JOURNAL_ID, {
        kind: 'credit_sale',
        customerId: CUSTOMER_ID,
        lines: [
          { account: 'cash', amountKobo: 500_000 },
          { account: RECEIVABLE, amountKobo: 1_000_000 },
          { account: 'sales', amountKobo: -1_500_000 },
        ],
        source: { inference: 'on_device', reason: 'DEFAULT_DEVICE', promptVersion: 'parse-v1' },
      }),
    );
    await assertSucceeds(batch.commit());
  });

  it('allows a repayment that increments an existing customer in the same batch', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc(CUSTOMER_ID, { balanceKobo: 1_000_000 }) });
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    batch.update(doc(db, customerPath(ALICE)), {
      balanceKobo: increment(-400_000),
      updatedAt: serverTimestamp(),
    });
    batch.set(
      doc(db, journalPath(ALICE)),
      journalDoc(JOURNAL_ID, {
        kind: 'repayment',
        customerId: CUSTOMER_ID,
        lines: [
          { account: 'cash', amountKobo: 400_000 },
          { account: RECEIVABLE, amountKobo: -400_000 },
        ],
        source: { inference: 'in_cloud', reason: 'LANGUAGE', promptVersion: 'parse-v1' },
      }),
    );
    await assertSucceeds(batch.commit());
  });

  it('allows a sale that keeps a resolved customerId', async () => {
    await seed(env(), { [customerPath(ALICE)]: storedCustomerDoc() });
    await assertSucceeds(createAsAlice(journalDoc(JOURNAL_ID, { customerId: CUSTOMER_ID })));
  });

  it('allows a reversal of an existing journal', async () => {
    await seed(env(), { [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc() });
    await assertSucceeds(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          kind: 'reversal',
          reverses: ORIGINAL_JOURNAL_ID,
          description: 'Reversal: 3 bag rice',
          lines: [
            { account: 'cash', amountKobo: -1_500_000 },
            { account: 'sales', amountKobo: 1_500_000 },
          ],
        }),
      ),
    );
  });

  it('allows a reversal of a credit sale with the customer increment in one batch', async () => {
    await seed(env(), {
      [customerPath(ALICE)]: storedCustomerDoc(CUSTOMER_ID, { balanceKobo: 1_000_000 }),
      [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc(ORIGINAL_JOURNAL_ID, {
        kind: 'credit_sale',
        customerId: CUSTOMER_ID,
        lines: [
          { account: RECEIVABLE, amountKobo: 1_000_000 },
          { account: 'sales', amountKobo: -1_000_000 },
        ],
      }),
    });
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    batch.update(doc(db, customerPath(ALICE)), {
      balanceKobo: increment(-1_000_000),
      updatedAt: serverTimestamp(),
    });
    batch.set(
      doc(db, journalPath(ALICE)),
      journalDoc(JOURNAL_ID, {
        kind: 'reversal',
        reverses: ORIGINAL_JOURNAL_ID,
        customerId: CUSTOMER_ID,
        description: 'Reversal: 3 bag rice',
        lines: [
          { account: RECEIVABLE, amountKobo: -1_000_000 },
          { account: 'sales', amountKobo: 1_000_000 },
        ],
      }),
    );
    await assertSucceeds(batch.commit());
  });

  it('allows the most expensive journal: 10-line reversal with a customer, in a batch', async () => {
    // Guards the 1000-expression limit: every check and both existsAfter calls run.
    await seed(env(), {
      [customerPath(ALICE)]: storedCustomerDoc(CUSTOMER_ID, { balanceKobo: 900 }),
      [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc(),
    });
    const lines = [];
    for (let i = 0; i < 9; i += 1) lines.push({ account: RECEIVABLE, amountKobo: -100 });
    lines.push({ account: 'sales', amountKobo: 900 });
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    batch.update(doc(db, customerPath(ALICE)), {
      balanceKobo: increment(-900),
      updatedAt: serverTimestamp(),
    });
    batch.set(
      doc(db, journalPath(ALICE)),
      journalDoc(JOURNAL_ID, {
        kind: 'reversal',
        reverses: ORIGINAL_JOURNAL_ID,
        customerId: CUSTOMER_ID,
        description: 'r'.repeat(120),
        lines,
        source: { inference: 'in_cloud', reason: 'r'.repeat(40), promptVersion: 'p'.repeat(40) },
      }),
    );
    await assertSucceeds(batch.commit());
  });

  it('allows a 120-char description and an empty description', async () => {
    await assertSucceeds(createAsAlice(journalDoc(JOURNAL_ID, { description: 'd'.repeat(120) })));
    await assertSucceeds(createAsAlice(journalDoc(ulid(200), { description: '' }), ulid(200)));
  });
});

describe('journals create: ownership and auth', () => {
  it("denies writing into another user's ledger", async () => {
    await assertFails(setDoc(doc(dbAs(env(), BOB), journalPath(ALICE)), journalDoc()));
  });

  it('denies an unauthenticated create', async () => {
    await assertFails(setDoc(doc(dbAnon(env()), journalPath(ALICE)), journalDoc()));
  });
});

describe('journals create: document shape', () => {
  for (const key of JOURNAL_KEYS) {
    it(`denies a missing ${key}`, async () => {
      await assertFails(createAsAlice(without(journalDoc(), key)));
    });
  }

  it('denies an extra field', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { note: 'free text' })));
  });

  it('denies an unknown schemaVersion', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { schemaVersion: 2 })));
  });

  it('denies id that does not match the document ID', async () => {
    await assertFails(createAsAlice(journalDoc(ulid(999))));
  });

  it('denies a document ID that is not a ULID', async () => {
    await assertFails(createAsAlice(journalDoc('journal-1'), 'journal-1'));
  });

  it('denies createdAt that is not request.time', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { createdAt: pastTimestamp() })));
  });

  it('denies an unknown kind', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { kind: 'gift' })));
  });

  it('denies a bad day', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { day: '23-11-2026' })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { day: '2026-11-23T00' })));
  });

  it('denies a bad occurredAt', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { occurredAt: 'yesterday' })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { occurredAt: 1732353300000 })));
  });

  it('denies a description over 120 chars', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { description: 'd'.repeat(121) })));
  });

  it('denies an idempotencyKey that is empty or over 80 chars', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { idempotencyKey: '' })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { idempotencyKey: 'k'.repeat(81) })));
  });

  it('denies a bad source', async () => {
    const base = { inference: 'manual', reason: 'TYPED', promptVersion: null };
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { source: { ...base, inference: 'magic' } })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { source: { ...base, reason: '' } })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { source: { ...base, reason: 'r'.repeat(41) } })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { source: { ...base, promptVersion: '' } })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { source: { ...base, text: 'raw note' } })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { source: without(base, 'promptVersion') })));
  });
});

describe('journals create: lines', () => {
  it('denies unbalanced lines', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          lines: [
            { account: 'cash', amountKobo: 1_500_000 },
            { account: 'sales', amountKobo: -1_499_999 },
          ],
        }),
      ),
    );
  });

  it('denies lines unbalanced only at the last index', async () => {
    const lines = balancedLines(10);
    lines[9] = { account: 'sales', amountKobo: -899 };
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines })));
  });

  it('denies too few lines (0 and 1)', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines: [] })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines: [{ account: 'cash', amountKobo: 1 }] })));
  });

  it('denies too many lines (11, balanced)', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines: balancedLines(11) })));
  });

  it('denies an 11th line hidden after 10 balanced lines', async () => {
    const lines = [...balancedLines(10), { account: 'cash', amountKobo: 5 }];
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines })));
  });

  it('denies lines that are not a list', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines: { 0: { account: 'cash', amountKobo: 0 } } })));
  });

  for (const account of [
    'bank',
    'Cash',
    'cash ',
    'receivable:',
    'receivable:123',
    `receivable:${CUSTOMER_ID.toLowerCase()}`,
    'receivable:01JD0000000000000000000000I',
    `receivable:${CUSTOMER_ID}x`,
  ]) {
    it(`denies the bad account string ${JSON.stringify(account)}`, async () => {
      await assertFails(
        createAsAlice(
          journalDoc(JOURNAL_ID, {
            lines: [
              { account, amountKobo: 100 },
              { account: 'sales', amountKobo: -100 },
            ],
          }),
        ),
      );
    });
  }

  it('denies a receivable line for a different customer than customerId', async () => {
    await seed(env(), {
      [customerPath(ALICE)]: storedCustomerDoc(),
      [customerPath(ALICE, OTHER_CUSTOMER_ID)]: storedCustomerDoc(OTHER_CUSTOMER_ID),
    });
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          kind: 'credit_sale',
          customerId: CUSTOMER_ID,
          lines: [
            { account: `receivable:${OTHER_CUSTOMER_ID}`, amountKobo: 100 },
            { account: 'sales', amountKobo: -100 },
          ],
        }),
      ),
    );
  });

  it('denies a receivable line when customerId is null', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          kind: 'credit_sale',
          customerId: null,
          lines: [
            { account: RECEIVABLE, amountKobo: 100 },
            { account: 'sales', amountKobo: -100 },
          ],
        }),
      ),
    );
  });

  it('denies a zero amount', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          lines: [
            { account: 'cash', amountKobo: 100 },
            { account: 'expenses', amountKobo: 0 },
            { account: 'sales', amountKobo: -100 },
          ],
        }),
      ),
    );
  });

  it('denies a non-integer amount', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          lines: [
            { account: 'cash', amountKobo: 100.5 },
            { account: 'sales', amountKobo: -100.5 },
          ],
        }),
      ),
    );
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          lines: [
            { account: 'cash', amountKobo: '100' },
            { account: 'sales', amountKobo: '-100' },
          ],
        }),
      ),
    );
  });

  it('denies a line with an extra key', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          lines: [
            { account: 'cash', amountKobo: 100, memo: 'x' },
            { account: 'sales', amountKobo: -100 },
          ],
        }),
      ),
    );
  });

  it('denies a line with a missing key', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          lines: [{ account: 'cash' }, { account: 'sales', amountKobo: -100 }],
        }),
      ),
    );
  });

  it('denies a line that is not a map', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines: ['cash:100', { account: 'sales', amountKobo: -100 }] })));
  });

  it('denies an invalid line at the last allowed index (9)', async () => {
    const lines: DocumentData[] = balancedLines(10);
    lines[9] = { account: 'bank', amountKobo: -900 };
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { lines })));
  });
});

describe('journals create: reversals', () => {
  it('denies a reversal without reverses', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          kind: 'reversal',
          reverses: null,
          lines: [
            { account: 'cash', amountKobo: -1_500_000 },
            { account: 'sales', amountKobo: 1_500_000 },
          ],
        }),
      ),
    );
  });

  it('denies a reversal whose reverses is not a ULID', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { kind: 'reversal', reverses: 'abc' })));
  });

  it('denies a reversal of a journal that does not exist', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { kind: 'reversal', reverses: ulid(555) })));
  });

  it('denies a non-reversal with reverses', async () => {
    await seed(env(), { [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc() });
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { reverses: ORIGINAL_JOURNAL_ID })));
  });
});

describe('journals create: customer reference', () => {
  it('denies a customerId whose customer does not exist after the batch', async () => {
    await assertFails(
      createAsAlice(
        journalDoc(JOURNAL_ID, {
          kind: 'credit_sale',
          customerId: CUSTOMER_ID,
          lines: [
            { account: RECEIVABLE, amountKobo: 100 },
            { account: 'sales', amountKobo: -100 },
          ],
        }),
      ),
    );
  });

  it("denies a customerId that exists only in another user's ledger", async () => {
    await seed(env(), { [customerPath(BOB)]: storedCustomerDoc() });
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { customerId: CUSTOMER_ID })));
  });

  it('denies a batch whose customer write fails validation', async () => {
    const db = dbAs(env(), ALICE);
    const batch = writeBatch(db);
    batch.set(doc(db, customerPath(ALICE)), customerDoc(CUSTOMER_ID, { name: '' }));
    batch.set(doc(db, journalPath(ALICE)), journalDoc(JOURNAL_ID, { customerId: CUSTOMER_ID }));
    await assertFails(batch.commit());
  });

  it('denies a non-ULID customerId even when that customer doc exists', async () => {
    await seed(env(), { [customerPath(ALICE, 'legacy-1')]: storedCustomerDoc('legacy-1') });
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { customerId: 'legacy-1' })));
  });

  it('denies a customerId that is not a ULID or not a string', async () => {
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { customerId: 'cust/../x' })));
    await assertFails(createAsAlice(journalDoc(JOURNAL_ID, { customerId: 42 })));
  });
});

describe('journals read and query', () => {
  async function seedLedger() {
    await seed(env(), {
      [customerPath(ALICE)]: storedCustomerDoc(),
      [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc(ORIGINAL_JOURNAL_ID, {
        customerId: CUSTOMER_ID,
      }),
    });
  }

  it('allows the owner to get a journal', async () => {
    await seedLedger();
    await assertSucceeds(getDoc(doc(dbAs(env(), ALICE), journalPath(ALICE, ORIGINAL_JOURNAL_ID))));
  });

  it('allows the owner to run every app query', async () => {
    await seedLedger();
    const journals = collection(dbAs(env(), ALICE), `users/${ALICE}/journals`);
    await assertSucceeds(
      getDocs(query(journals, where('day', '>=', '2026-11-01'), where('day', '<=', '2026-11-30'), orderBy('day'), orderBy('occurredAt', 'desc'))),
    );
    await assertSucceeds(getDocs(query(journals, where('customerId', '==', CUSTOMER_ID), orderBy('occurredAt', 'desc'))));
    await assertSucceeds(getDocs(query(journals, where('reverses', '==', ORIGINAL_JOURNAL_ID))));
  });

  it("denies reading or querying another user's journals", async () => {
    await seedLedger();
    const db = dbAs(env(), BOB);
    await assertFails(getDoc(doc(db, journalPath(ALICE, ORIGINAL_JOURNAL_ID))));
    await assertFails(getDocs(query(collection(db, `users/${ALICE}/journals`), orderBy('occurredAt', 'desc'))));
  });

  it('denies an unauthenticated read', async () => {
    await seedLedger();
    await assertFails(getDoc(doc(dbAnon(env()), journalPath(ALICE, ORIGINAL_JOURNAL_ID))));
  });
});

describe('journals are immutable', () => {
  it('denies any update, even a no-op field rewrite', async () => {
    await seed(env(), { [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc() });
    const db = dbAs(env(), ALICE);
    await assertFails(updateDoc(doc(db, journalPath(ALICE, ORIGINAL_JOURNAL_ID)), { description: 'changed' }));
    await assertFails(setDoc(doc(db, journalPath(ALICE, ORIGINAL_JOURNAL_ID)), journalDoc(ORIGINAL_JOURNAL_ID)));
  });

  it('denies delete when deletion is not requested', async () => {
    await seed(env(), { [journalPath(ALICE, ORIGINAL_JOURNAL_ID)]: storedJournalDoc() });
    await assertFails(deleteDoc(doc(dbAs(env(), ALICE), journalPath(ALICE, ORIGINAL_JOURNAL_ID))));
  });
});
