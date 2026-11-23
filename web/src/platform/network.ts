/** Online state from `navigator.onLine` plus a Firestore reachability signal. Never trusts `onLine` alone. */
export interface NetworkMonitor {
  isOnline(): boolean;
  /** Metered hint from the Network Information API, where Chrome provides it. */
  saveDataHint(): boolean;
  /** Firestore reports whether its last snapshot came from the server. */
  reportServerReachable(reachable: boolean): void;
  subscribe(listener: (online: boolean) => void): () => void;
}

interface NetworkInformationLike {
  readonly saveData?: boolean;
}

/** Creates the browser network monitor. */
export function createNetworkMonitor(target: Window = window): NetworkMonitor {
  const listeners = new Set<(online: boolean) => void>();
  let serverReachable: boolean | null = null;
  let last = target.navigator.onLine;

  const compute = (): boolean => target.navigator.onLine && serverReachable !== false;
  const emit = (): void => {
    const now = compute();
    if (now === last) return;
    last = now;
    for (const listener of listeners) listener(now);
  };
  const onBrowserChange = (): void => {
    // A browser "online" event resets the server signal until Firestore confirms again.
    if (target.navigator.onLine) serverReachable = null;
    emit();
  };
  target.addEventListener('online', onBrowserChange);
  target.addEventListener('offline', onBrowserChange);

  return {
    isOnline: compute,
    saveDataHint() {
      const connection: unknown = Reflect.get(target.navigator, 'connection');
      return typeof connection === 'object' && connection !== null && (connection as NetworkInformationLike).saveData === true;
    },
    reportServerReachable(reachable) {
      serverReachable = reachable;
      emit();
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}
