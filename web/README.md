# Kpakam web app

The Kpakam web app (PWA) for Chrome. It works offline, installs to the home
screen, and uses Firebase AI Logic hybrid inference.

## Commands

Run these from the repository root.

| Task                                            | Command                                    |
| ----------------------------------------------- | ------------------------------------------ |
| Start with local emulators and a scripted model | `npm run dev -w @kpakam/web -- --mode e2e` |
| Start against your Firebase project             | `npm run dev -w @kpakam/web`               |
| Build                                           | `npm run build`                            |
| Check bundle budgets (after a build)            | `npm run budgets -w @kpakam/web`           |
| Unit and component tests                        | `npm test -w @kpakam/web`                  |
| Tests with coverage thresholds                  | `npm run test:coverage -w @kpakam/web`     |
| End-to-end journeys                             | `npm run test:e2e -w @kpakam/web`          |
| Cloud evaluation of the golden sets             | `npm run eval:cloud -w @kpakam/web`        |

For local emulators, start them first:
`npx firebase emulators:start --only auth,firestore --project demo-kpakam-e2e --config firebase.e2e.json`

## Settings

Copy `.env.example` to `.env.local` and fill in your Firebase web config.
`.env.e2e` holds the emulator settings for tests. It holds no secrets.

## How the code is organised

```text
src/
├── app/          # Start-up, router, app shell, service wiring
├── features/     # One folder per feature: quick-entry, ledger, customers, dashboard, settings, insights
├── inference/    # Router, prompts, model clients, AI job queue
├── platform/     # Thin wrappers over Firebase, network, battery, speech, recorder
└── shared/       # UI kit, money, ids, time, messages
```

Code flows one way: **component → hook → service → repository → platform →
SDK**.

- Domain code (`*/domain/`) is pure. It does no I/O.
- Only `platform/` and repository files may import Firebase. ESLint enforces
  this.
- Features use each other only through their `index.ts`.
- Every string the trader sees is in `src/shared/messages/en.ts`. Use plain
  words; see [ADR 0009](../docs/adr/0009-plain-language-ui.md).

## Limits that CI enforces

| Limit                      | Value                              |
| -------------------------- | ---------------------------------- |
| Shell JavaScript (gzip)    | 170 KB                             |
| Largest route chunk (gzip) | 60 KB                              |
| Function length            | 40 lines (components: 150)         |
| Cyclomatic complexity      | 10                                 |
| File length                | 300 lines                          |
| Function parameters        | 2 (use an options object for more) |
| Domain test coverage       | 95% lines and branches             |
