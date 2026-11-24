import { createRootRoute, createRoute, createRouter, lazyRouteComponent } from '@tanstack/react-router';
import { TodayPage } from '@/features/dashboard';
import { AppShell } from './app-shell';

const root = createRootRoute({ component: AppShell });

// The Today screen ships in the shell; other screens load on demand (route-level code splitting).
const today = createRoute({ getParentRoute: () => root, path: '/', component: TodayPage });
const history = createRoute({ getParentRoute: () => root, path: '/history', component: lazyRouteComponent(() => import('@/features/ledger'), 'HistoryPage') });
const customers = createRoute({
  getParentRoute: () => root,
  path: '/customers',
  component: lazyRouteComponent(() => import('@/features/customers'), 'DebtBookPage'),
});
const CustomerPage = lazyRouteComponent(() => import('@/features/customers'), 'CustomerPage');
const customer = createRoute({
  getParentRoute: () => root,
  path: '/customers/$customerId',
  component: function CustomerRoute() {
    const { customerId } = customer.useParams();
    return <CustomerPage customerId={customerId} />;
  },
});
const settings = createRoute({
  getParentRoute: () => root,
  path: '/settings',
  component: lazyRouteComponent(() => import('@/features/settings'), 'SettingsPage'),
});
const insights = createRoute({
  getParentRoute: () => root,
  path: '/insights',
  component: lazyRouteComponent(() => import('@/features/insights'), 'InsightsPage'),
});

// Dev builds only: golden-set evaluation against the on-device model. The DEV constant is
// replaced at build time, so production bundles drop this route and its chunk entirely.
const devRoutes = import.meta.env.DEV
  ? [createRoute({ getParentRoute: () => root, path: '/dev/eval', component: lazyRouteComponent(() => import('@/features/dev-eval'), 'EvalPage') })]
  : [];

/** The typed route tree. */
export const router = createRouter({
  routeTree: root.addChildren([today, history, customers, customer, settings, insights, ...devRoutes]),
  defaultPreload: 'intent',
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
