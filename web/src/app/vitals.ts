import type { FirebaseApp } from 'firebase/app';

let started = false;

/** Sends Web Vitals to Analytics, only after the trader opts in (PRD Privacy). */
export async function startVitals(app: FirebaseApp): Promise<void> {
  if (started) return;
  started = true;
  const [{ onCLS, onINP, onLCP }, analytics] = await Promise.all([import('web-vitals'), import('firebase/analytics')]);
  if (!(await analytics.isSupported())) return;
  const instance = analytics.getAnalytics(app);
  const send = (metric: { name: string; value: number; rating: string }) =>
    analytics.logEvent(instance, 'web_vital', { metric: metric.name, value: Math.round(metric.value * 1000) / 1000, rating: metric.rating });
  onCLS(send);
  onINP(send);
  onLCP(send);
}
