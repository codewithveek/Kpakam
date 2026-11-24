import { useEffect } from 'react';
import { useAppStore } from './app-store';
import type { Services } from './services';
import { startVitals } from './vitals';

function whenIdle(task: () => void): void {
  if ('requestIdleCallback' in window) window.requestIdleCallback(task, { timeout: 5000 });
  else setTimeout(task, 1500);
}

/**
 * Wires long-lived listeners once services exist: settings, network, queue drain,
 * Remote Config, and idle-time warm-up of the on-device models.
 */
export function useStartup(services: Services): void {
  useEffect(() => {
    const store = useAppStore.getState();
    const { queue, network, remoteConfig, user, inference, speech } = services;
    const stopSettings = user.watchSettings((settings) => {
      useAppStore.getState().setSettings(settings);
      if (settings.analyticsOptIn) void startVitals(services.app);
    });
    void queue.recover().then(() => queue.drain());
    store.setOnline(network.isOnline());
    if (network.isOnline()) void remoteConfig.refresh();
    const stopNetwork = network.subscribe((online) => {
      useAppStore.getState().setOnline(online);
      if (online) void remoteConfig.refresh().then(() => queue.drain());
    });
    const onVisible = () => document.visibilityState === 'visible' && network.isOnline() && void queue.drain();
    document.addEventListener('visibilitychange', onVisible);
    whenIdle(() => {
      void inference.deviceModelState().then((state) => {
        useAppStore.getState().setDeviceModel(state);
        // Warm only when already downloaded: a download needs a user gesture (first focus).
        if (state === 'available') void inference.prepareDeviceModel();
      });
      // Ask about local speech only for traders who speak English entries.
      if (useAppStore.getState().settings.voiceLanguage === 'en') {
        void speech.localState().then(async (state) => {
          if (state === 'downloadable') await speech.installLocal();
        });
      }
    });
    return () => {
      stopSettings();
      stopNetwork();
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [services]);
}
