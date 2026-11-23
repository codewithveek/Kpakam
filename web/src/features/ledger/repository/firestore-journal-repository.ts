import {
  collection,
  doc,
  getDocFromCache,
  getDocs,
  getDocsFromCache,
  increment,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  where,
  writeBatch,
  type CollectionReference,
  type Firestore,
  type Query,
  type QuerySnapshot,
  type WriteBatch,
} from 'firebase/firestore';
import type { CustomerChange, JournalEntry, JournalRepository, LedgerCommit } from './journal-repository';
import { decodeJournal, encodeJournal } from './journal-codec';
import type { Journal } from '../domain/journal';
import { encodeNewCustomer } from './customer-codec';

export interface FirestoreRepoContext {
  readonly db: Firestore;
  readonly uid: string;
  /** Called with `true` when a snapshot came from the server, `false` when only from cache. */
  readonly onServerSignal?: (reachable: boolean) => void;
}

function entriesOf(snapshot: QuerySnapshot): JournalEntry[] {
  return snapshot.docs.flatMap((d) => {
    const journal = decodeJournal(d.data());
    return journal === null ? [] : [{ journal, pendingSync: d.metadata.hasPendingWrites }];
  });
}

async function getDocsPreferCache(q: Query): Promise<QuerySnapshot> {
  try {
    return await getDocsFromCache(q);
  } catch {
    return getDocs(q);
  }
}

interface RepoRefs {
  readonly ctx: FirestoreRepoContext;
  readonly journals: CollectionReference;
  readonly customers: CollectionReference;
}

function watch(refs: RepoRefs, spec: { q: Query; onChange: (entries: readonly JournalEntry[]) => void }): () => void {
  return onSnapshot(spec.q, { includeMetadataChanges: true }, (snapshot) => {
    refs.ctx.onServerSignal?.(!snapshot.metadata.fromCache);
    spec.onChange(entriesOf(snapshot));
  });
}

function applyCustomer(refs: RepoRefs, op: { batch: WriteBatch; change: CustomerChange }): void {
  const { batch, change } = op;
  if (change.type === 'create') {
    batch.set(doc(refs.customers, change.customer.id), { ...encodeNewCustomer(change.customer), createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
  } else {
    batch.update(doc(refs.customers, change.customerId), { balanceKobo: increment(change.delta), updatedAt: serverTimestamp() });
  }
}

/**
 * Applies journals and their customer cache changes in one atomic batch. Offline, `commit()`
 * resolves only after the server acks; the local cache and listeners update at once, so the
 * trader never waits.
 */
function commit(refs: RepoRefs, change: LedgerCommit): Promise<void> {
  const batch = writeBatch(refs.ctx.db);
  for (const customer of change.customers) applyCustomer(refs, { batch, change: customer });
  for (const journal of change.journals) {
    batch.set(doc(refs.journals, journal.id), { ...encodeJournal(journal), createdAt: serverTimestamp() });
  }
  void batch.commit().catch((error: unknown) => {
    console.error('Ledger write rejected', error);
  });
  return Promise.resolve();
}

async function existsInCache(refs: RepoRefs, journalId: string): Promise<boolean> {
  try {
    return (await getDocFromCache(doc(refs.journals, journalId))).exists();
  } catch {
    return false;
  }
}

async function listAll(refs: RepoRefs): Promise<Journal[]> {
  const snapshot = await getDocsPreferCache(query(refs.journals, orderBy('occurredAt')));
  return snapshot.docs.map((d) => decodeJournal(d.data())).filter((j): j is Journal => j !== null);
}

/** Firestore journal repository under `users/{uid}/journals`. */
export function createFirestoreJournalRepository(ctx: FirestoreRepoContext): JournalRepository {
  const refs: RepoRefs = { ctx, journals: collection(ctx.db, 'users', ctx.uid, 'journals'), customers: collection(ctx.db, 'users', ctx.uid, 'customers') };
  return {
    watchRange: ({ from, to }, onChange) =>
      watch(refs, { q: query(refs.journals, where('day', '>=', from), where('day', '<=', to), orderBy('day'), orderBy('occurredAt', 'desc')), onChange }),
    watchCustomer: (customerId, onChange) =>
      watch(refs, { q: query(refs.journals, where('customerId', '==', customerId), orderBy('occurredAt', 'desc')), onChange }),
    exists: (journalId) => existsInCache(refs, journalId),
    isReversed: async (journalId) => !(await getDocsPreferCache(query(refs.journals, where('reverses', '==', journalId), limit(1)))).empty,
    commit: (change) => commit(refs, change),
    listAll: () => listAll(refs),
  };
}
