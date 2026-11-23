import { collection, getDocs, getDocsFromCache, onSnapshot, orderBy, query, type QuerySnapshot } from 'firebase/firestore';
import type { Customer } from '../domain/customer';
import type { CustomerRepository } from './journal-repository';
import type { FirestoreRepoContext } from './firestore-journal-repository';
import { decodeCustomer } from './customer-codec';

function customersOf(snapshot: QuerySnapshot): Customer[] {
  return snapshot.docs.map((d) => decodeCustomer(d.data())).filter((c): c is Customer => c !== null);
}

/** Firestore customer repository under `users/{uid}/customers`. */
export function createFirestoreCustomerRepository(ctx: FirestoreRepoContext): CustomerRepository {
  const q = query(collection(ctx.db, 'users', ctx.uid, 'customers'), orderBy('balanceKobo', 'desc'));
  return {
    watchAll(onChange) {
      return onSnapshot(q, (snapshot) => onChange(customersOf(snapshot)));
    },
    async list() {
      try {
        return customersOf(await getDocsFromCache(q));
      } catch {
        return customersOf(await getDocs(q));
      }
    },
  };
}
