import { collection, deleteDoc, doc, getDocs, onSnapshot, serverTimestamp, setDoc, updateDoc, writeBatch, type Firestore } from 'firebase/firestore';
import { decodeSettings, DEFAULT_SETTINGS, type TraderSettings } from '../domain/settings';

/** The trader's profile document and account-level operations. */
export interface UserRepository {
  watchSettings(onChange: (settings: TraderSettings) => void): () => void;
  saveSettings(settings: TraderSettings): Promise<void>;
  /** Marks the account for deletion (irreversible), then deletes every document. */
  deleteAllData(): Promise<void>;
}

const SUBCOLLECTIONS = ['journals', 'customers', 'aiEvents'] as const;
const BATCH_SIZE = 400;

/** Firestore user repository for `users/{uid}`. Creates the profile on first use. */
export function createFirestoreUserRepository(db: Firestore, uid: string): UserRepository {
  const userDoc = doc(db, 'users', uid);
  let exists = false;
  return {
    watchSettings(onChange) {
      return onSnapshot(userDoc, (snapshot) => {
        exists = snapshot.exists();
        if (!exists && !snapshot.metadata.fromCache) {
          void setDoc(userDoc, { settings: { ...DEFAULT_SETTINGS }, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), schemaVersion: 1 });
        }
        onChange(decodeSettings(snapshot.get('settings')));
      });
    },
    async saveSettings(settings) {
      const data = { settings: { ...settings }, updatedAt: serverTimestamp() };
      if (exists) void updateDoc(userDoc, data);
      else void setDoc(userDoc, { ...data, createdAt: serverTimestamp(), schemaVersion: 1 });
      return Promise.resolve();
    },
    async deleteAllData() {
      await updateDoc(userDoc, { deletionRequestedAt: serverTimestamp(), updatedAt: serverTimestamp() });
      for (const name of SUBCOLLECTIONS) {
        const snapshot = await getDocs(collection(db, 'users', uid, name));
        for (let i = 0; i < snapshot.docs.length; i += BATCH_SIZE) {
          const batch = writeBatch(db);
          snapshot.docs.slice(i, i + BATCH_SIZE).forEach((d) => batch.delete(d.ref));
          await batch.commit();
        }
      }
      await deleteDoc(userDoc);
    },
  };
}
