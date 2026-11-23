import { readAppConfig } from '@/platform/config';
import { initFirebase } from '@/platform/firebase/app';
import { createAuthAdapter } from '@/platform/firebase/auth-adapter';
import { createRemoteConfigAdapter } from '@/platform/firebase/remote-config-adapter';
import { createNetworkMonitor } from '@/platform/network';
import { createSpeechAdapter } from '@/platform/speech';
import type { InferenceClient } from '@/inference/clients/inference-client';
import type { FirebaseApp } from 'firebase/app';
import { useAppStore } from './app-store';
import { createServices } from './create-services';
import type { Services } from './services';

export type BootResult =
  | { readonly type: 'ready'; readonly services: Services }
  | { readonly type: 'not-configured' }
  | { readonly type: 'needs-network' }
  | { readonly type: 'failed' };

async function createInference(spec: { app: FirebaseApp; fake: boolean; cloudModelName: () => string; appCheck: boolean }): Promise<InferenceClient> {
  const { app, cloudModelName, appCheck } = spec;
  if (spec.fake) {
    const { createE2eInferenceClient } = await import('@/inference/clients/e2e-inference-client');
    return createE2eInferenceClient();
  }
  const { createFirebaseInferenceClient } = await import('@/inference/clients/firebase-inference-client');
  return createFirebaseInferenceClient({ app, cloudModelName, useLimitedUseAppCheckTokens: appCheck });
}

let booting: Promise<BootResult> | null = null;

/**
 * Starts Firebase, signs the trader in (anonymously the first time), and builds the services.
 * Idempotent: React StrictMode and retries share one boot.
 */
export function bootstrap(): Promise<BootResult> {
  booting ??= boot().then((result) => {
    if (result.type !== 'ready') booting = null;
    return result;
  });
  return booting;
}

async function boot(): Promise<BootResult> {
  const config = readAppConfig();
  if (config.firebase === null) return { type: 'not-configured' };
  const handles = initFirebase({ ...config, firebase: config.firebase });
  const auth = createAuthAdapter(handles.auth);
  const signedIn = await auth.ensureSignedIn();
  if (!signedIn.ok) return { type: signedIn.error === 'NEEDS_NETWORK' ? 'needs-network' : 'failed' };
  useAppStore.getState().setIdentity(signedIn.value);
  const remoteConfig = createRemoteConfigAdapter(handles.app, { fetchEnabled: !config.useEmulators });
  const inference = await createInference({
    app: handles.app,
    fake: config.fakeInference,
    cloudModelName: () => remoteConfig.values().cloudModelName,
    appCheck: handles.appCheckEnabled,
  });
  const services = createServices({
    handles,
    uid: signedIn.value.uid,
    auth,
    inference,
    network: createNetworkMonitor(),
    remoteConfig,
    speech: createSpeechAdapter(),
  });
  return { type: 'ready', services };
}
