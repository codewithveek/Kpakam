# 0008. Load Firebase after the first screen paints

## Status

Accepted, 23 November 2026.

## Context

The PRD budget for initial JavaScript is 170 KB gzip. The Firestore SDK alone is
about 164 KB gzip, and traders use low-end phones on slow networks.

## Decision

- The service context module holds types only. Firebase, the repositories, and
  the services load through one dynamic import (`app/bootstrap.ts`).
- `firebase/ai` loads only on the first AI request. Remote Config and Analytics
  load lazily too.
- Zod uses `zod/mini` (tree-shakable).
- `web/scripts/check-budgets.mjs` walks the Vite manifest and fails the build
  when the shell or a route chunk breaks its budget.

## Consequences

- The shell is about 135 KB gzip and paints before the SDK is parsed.
- Firebase (about 220 KB gzip) loads in parallel right after first paint. The
  service worker caches it for repeat visits.
- Code that runs at startup must not import Firebase directly. ESLint blocks
  Firebase imports outside the platform and repository layers.
