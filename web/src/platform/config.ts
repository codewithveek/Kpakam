import type { FirebaseOptions } from 'firebase/app';

/** Build-time settings from `web/.env.local` (see `.env.example`). Firebase web config is public by design. */
export interface AppConfig {
  readonly firebase: FirebaseOptions | null;
  readonly recaptchaSiteKey: string | null;
  readonly appCheckDebugToken: string | boolean;
  readonly useEmulators: boolean;
  readonly emulatorPorts: { readonly auth: number; readonly firestore: number };
  readonly fakeInference: boolean;
}

function value(raw: unknown): string | null {
  return typeof raw === 'string' && raw.trim() !== '' ? raw.trim() : null;
}

type Env = ImportMetaEnv;

function firebaseOptions(env: Env, useEmulators: boolean): FirebaseOptions | null {
  const demo = (real: string | null, fallback: string) => real ?? (useEmulators ? fallback : null);
  const apiKey = demo(value(env.VITE_FIREBASE_API_KEY), 'demo-api-key');
  const projectId = demo(value(env.VITE_FIREBASE_PROJECT_ID), 'demo-kpakam');
  const appId = demo(value(env.VITE_FIREBASE_APP_ID), '1:000000000000:web:demo');
  if (apiKey === null || projectId === null || appId === null) return null;
  const optional = (key: 'messagingSenderId' | 'measurementId', raw: unknown) => {
    const v = value(raw);
    return v === null ? {} : { [key]: v };
  };
  return {
    apiKey,
    projectId,
    appId,
    authDomain: value(env.VITE_FIREBASE_AUTH_DOMAIN) ?? `${projectId}.firebaseapp.com`,
    ...optional('messagingSenderId', env.VITE_FIREBASE_MESSAGING_SENDER_ID),
    ...optional('measurementId', env.VITE_FIREBASE_MEASUREMENT_ID),
  };
}

/** Reads the app config once. `firebase` is null when the project is not configured. */
export function readAppConfig(env: Env = import.meta.env): AppConfig {
  const useEmulators = env.VITE_USE_EMULATORS === 'true';
  return {
    firebase: firebaseOptions(env, useEmulators),
    recaptchaSiteKey: value(env.VITE_RECAPTCHA_ENTERPRISE_SITE_KEY),
    appCheckDebugToken: env.DEV ? (value(env.VITE_APPCHECK_DEBUG_TOKEN) ?? true) : false,
    useEmulators,
    emulatorPorts: { auth: Number(env.VITE_EMULATOR_AUTH_PORT ?? 9099), firestore: Number(env.VITE_EMULATOR_FIRESTORE_PORT ?? 8080) },
    fakeInference: env.VITE_FAKE_INFERENCE === 'true',
  };
}
