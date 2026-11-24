/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_FIREBASE_API_KEY?: string;
  readonly VITE_FIREBASE_AUTH_DOMAIN?: string;
  readonly VITE_FIREBASE_PROJECT_ID?: string;
  readonly VITE_FIREBASE_APP_ID?: string;
  readonly VITE_FIREBASE_MESSAGING_SENDER_ID?: string;
  readonly VITE_FIREBASE_MEASUREMENT_ID?: string;
  readonly VITE_RECAPTCHA_ENTERPRISE_SITE_KEY?: string;
  readonly VITE_APPCHECK_DEBUG_TOKEN?: string;
  readonly VITE_USE_EMULATORS?: string;
  readonly VITE_FAKE_INFERENCE?: string;
  readonly VITE_EMULATOR_AUTH_PORT?: string;
  readonly VITE_EMULATOR_FIRESTORE_PORT?: string;
}
