import { createMemoryHistory, createRootRoute, createRoute, createRouter, Outlet, RouterProvider } from '@tanstack/react-router';
import { render } from '@testing-library/react';
import type { FunctionComponent } from 'react';
import { ServicesContext, type Services } from '@/app/services';

/** Renders one screen inside a memory router and the services context. */
export function renderScreen(Screen: FunctionComponent, options: { services: Services; path?: string }) {
  const root = createRootRoute({ component: Outlet });
  const paths = ['/', '/history', '/customers', '/customers/$customerId', '/settings', '/insights'];
  const routes = paths.map((path) => createRoute({ getParentRoute: () => root, path, component: Screen }));
  const router = createRouter({ routeTree: root.addChildren(routes), history: createMemoryHistory({ initialEntries: [options.path ?? '/'] }) });
  return render(
    <ServicesContext.Provider value={options.services}>
      <RouterProvider router={router} />
    </ServicesContext.Provider>,
  );
}
