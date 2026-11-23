# @kpakam/firebase

This folder holds the Firebase back end for Kpakam. There is no custom server.
The apps talk to Firestore directly. The security rules are the only guard.

| File                         | What it does                                                           |
| ---------------------------- | ---------------------------------------------------------------------- |
| `firestore.rules`            | Security rules for Firestore                                           |
| `firestore.indexes.json`     | Composite indexes for the app queries                                  |
| `remoteconfig.template.json` | Remote Config keys and defaults                                        |
| `tests/*.test.ts`            | Rules tests that run on the Firestore emulator                         |
| `../firebase.json`           | Project config: rules, indexes, Remote Config, Hosting, emulators      |
| `../.firebaserc`             | Default project. It is `demo-kpakam`. Replace it with your project ID. |

The rules are a prototype. Review them before you share the app widely.

## Run the rules tests

You need Node 20 or later and Java 11 or later.

1. Go to the repository root.
2. Run `npm run test:rules`.
3. Read the result. All tests must pass.

The command starts the Firestore emulator and runs `vitest run`.
The first run downloads the emulator. This can take one minute.

To check the test types, run `npm run typecheck -w @kpakam/firebase`.

## Deploy

Do these steps from the repository root.

1. Log in: `npx firebase login`.
2. Select your project: `npx firebase use <your-project-id>`.
3. Run the rules tests. Do not deploy if a test fails.
4. Deploy the rules and indexes: `npx firebase deploy --only firestore`.
5. Deploy Remote Config: `npx firebase deploy --only remoteconfig`.
6. Build the web app: `npm run build`.
7. Deploy Hosting: `npx firebase deploy --only hosting`.

Note: step 5 replaces the live Remote Config template. Remote Config changes
made in the console are lost. Copy them into `remoteconfig.template.json` first.

Note: keep `remoteconfig.template.json` and
`packages/contract/remote-config.defaults.json` the same. The apps bundle the
contract file as their in-app defaults.

## Security model

### Who can do what

- All data is under `users/{uid}`.
- Only the signed-in owner can read or write it (`request.auth.uid == uid`).
- Signed-out clients can do nothing.
- Any other path is denied.

### Rules per collection

| Path             | Read  | Create | Update             | Delete                        |
| ---------------- | ----- | ------ | ------------------ | ----------------------------- |
| `users/{uid}`    | owner | owner  | owner              | owner, after deletion request |
| `customers/{id}` | owner | owner  | owner, some fields | owner, after deletion request |
| `journals/{id}`  | owner | owner  | never              | owner, after deletion request |
| `aiEvents/{id}`  | owner | owner  | never              | owner, after deletion request |

### Data checks

- Every document must have exactly the expected keys. Extra keys are denied.
- Every string has a length limit.
- `schemaVersion` must be `1`.
- `createdAt` must be `serverTimestamp()`.
- `updatedAt` must be `serverTimestamp()` on every write.
- Customer and journal IDs must be ULIDs.
- `id` must equal the document ID.

### Journals

- A journal cannot change. To fix a mistake, write a reversal.
- A journal has 2 to 10 lines.
- Each amount is a non-zero integer in kobo.
- The lines must sum to zero.
- An account is `cash`, `sales`, `expenses`, or `receivable:<customerId>`.
  The receivable account must name the journal's own `customerId`.
- A reversal must name an existing journal in `reverses`.
  Other kinds must have `reverses: null`.
- If `customerId` is set, the customer must exist when the batch commits.

### AI events

AI events hold telemetry only. The fixed key list blocks trader text.
Do not add a text field. The rules deny it.

### Account deletion

Account deletion cannot be undone.

1. Set `deletionRequestedAt` to `serverTimestamp()` on `users/{uid}`.
   Wait for this write to commit on the server.
2. Delete all documents in `customers`, `journals`, and `aiEvents`.
3. Delete `users/{uid}` last. You can do this in the same batch as step 2.
4. Delete the Firebase Auth user.

After step 1, `deletionRequestedAt` cannot change or be removed.
Step 2 fails if step 1 is not committed, or if the user doc is gone.

### Rules limits

Firestore rules evaluate at most 1000 expressions per request. The journal
check is the heaviest rule. The worst case is a 10-line reversal with a
customer. It uses about 80% of the limit. A test covers this case.

- Run the tests after every rules change.
- Keep the per-line checks small. Do not call a helper with the whole
  `lines` list for each index. An early version did this and hit the limit.

## What the apps must do

- Write `createdAt` and `updatedAt` with `serverTimestamp()`.
- Change `balanceKobo` with `increment()`. Send `updatedAt: serverTimestamp()`
  in the same update.
- Write a journal and its customer change in one `writeBatch`.
  Create a new customer in that batch.
- Use a ULID for every customer ID and journal ID.
- Write `day` (Africa/Lagos `YYYY-MM-DD`) and `customerId` (or `null`) on
  every journal.
- Write `occurredAt` as RFC 3339 with seconds and an offset, for example
  `2026-11-23T10:15:00.000+01:00`.

## Hosting headers

`firebase.json` sets a strict Content Security Policy for the web app.

- Scripts load only from the app origin and from Google reCAPTCHA,
  `apis.google.com` (Auth), and `www.googletagmanager.com` (Analytics).
  Inline scripts are blocked. Register the service worker from a file, not
  from an inline script.
- `style-src` allows `'unsafe-inline'`. The app ships no inline styles, but
  the reCAPTCHA widget can add inline styles. Remove `'unsafe-inline'` if you do
  not use reCAPTCHA or App Check with reCAPTCHA.
- If you add a Google or Firebase service, add its host to `connect-src`.
- Hashed files under `/assets/` are cached for one year.
  `index.html`, `sw.js`, `manifest.webmanifest`, and app routes are not cached.
