import { RouterProvider } from '@tanstack/react-router';
import { Component, useEffect, useState, type ReactNode } from 'react';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import type { BootResult } from './bootstrap';
import { router } from './router';
import { ServicesContext, type Services } from './services';
import { useStartup } from './use-startup';

/** Logs render errors (no trader text) and keeps saved data safe behind a friendly message. */
class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  override state = { failed: false };
  static getDerivedStateFromError(): { failed: boolean } {
    return { failed: true };
  }
  override componentDidCatch(error: Error): void {
    console.error('UI error', error.name, error.message);
  }
  override render(): ReactNode {
    return this.state.failed ? <Message text={MESSAGES.errors.generic} retry /> : this.props.children;
  }
}

function Message({ text, retry = false }: { readonly text: string; readonly retry?: boolean }) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-4 p-6 text-center">
      <p className="text-3xl font-extrabold text-primary">{MESSAGES.appName}</p>
      <p className="text-lg">{text}</p>
      {retry && <Button onClick={() => window.location.reload()}>{MESSAGES.setup.retry}</Button>}
    </main>
  );
}

function Ready({ services }: { readonly services: Services }) {
  useStartup(services);
  return (
    <ServicesContext.Provider value={services}>
      <RouterProvider router={router} />
    </ServicesContext.Provider>
  );
}

/** Root component: boots Firebase and services, then renders the app. */
export function App() {
  const [boot, setBoot] = useState<BootResult | null>(null);
  useEffect(() => {
    // Firebase loads in its own chunks, so the shell paints before the SDK is parsed.
    import('./bootstrap').then(({ bootstrap }) => bootstrap()).then(setBoot, () => setBoot({ type: 'failed' }));
  }, []);
  let content: ReactNode;
  if (boot === null) content = <Message text={MESSAGES.quickEntry.working} />;
  else if (boot.type === 'ready') content = <Ready services={boot.services} />;
  else if (boot.type === 'not-configured') content = <Message text={MESSAGES.setup.notConfigured} />;
  else content = <Message text={boot.type === 'needs-network' ? MESSAGES.setup.needsNetwork : MESSAGES.errors.generic} retry />;
  return <ErrorBoundary>{content}</ErrorBoundary>;
}
