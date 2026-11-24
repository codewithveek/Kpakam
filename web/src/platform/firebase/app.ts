import { initializeApp, type FirebaseApp } from 'firebase/app';
import { initializeAppCheck, ReCaptchaEnterpriseProvider } from 'firebase/app-check';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import { connectFirestoreEmulator, initializeFirestore, persistentLocalCache, persistentMultipleTabManager, type Firestore } from 'firebase/firestore';
import type { AppConfig } from '../config';

declare global {
  // App Check reads this global before initialisation (debug provider).
  var FIREBASE_APPCHECK_DEBUG_TOKEN: string | boolean | undefined;
}

export interface FirebaseHandles {
  readonly app: FirebaseApp;
  readonly auth: Auth;
  readonly db: Firestore;
  readonly appCheckEnabled: boolean;
}

function startAppCheck(app: FirebaseApp, config: AppConfig): boolean {
  if (config.useEmulators || config.recaptchaSiteKey === null) return false;
  if (config.appCheckDebugToken !== false) {
    // Debug tokens come from a gitignored .env.local or CI secrets; never committed.
    globalThis.FIREBASE_APPCHECK_DEBUG_TOKEN = config.appCheckDebugToken;
  }
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(config.recaptchaSiteKey),
    isTokenAutoRefreshEnabled: true,
  });
  return true;
}

/**
 * Initialises Firebase once: App Check first (so every request carries a token),
 * then Auth and Firestore with a persistent multi-tab offline cache.
 */
export function initFirebase(config: AppConfig & { firebase: NonNullable<AppConfig['firebase']> }): FirebaseHandles {
  const app = initializeApp(config.firebase);
  const appCheckEnabled = startAppCheck(app, config);
  const auth = getAuth(app);
  const db = initializeFirestore(app, {
    localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
  });
  if (config.useEmulators) {
    connectAuthEmulator(auth, `http://127.0.0.1:${config.emulatorPorts.auth}`, { disableWarnings: true });
    connectFirestoreEmulator(db, '127.0.0.1', config.emulatorPorts.firestore);
  }
  return { app, auth, db, appCheckEnabled };
}
