# Kpakam runbook

This runbook tells you how to set up, deploy, and operate Kpakam. Follow the
steps in order. Each step does one thing.

## 1. Create the Firebase project (once)

1. Create a Firebase project in the Firebase console.
2. Turn on Cloud Firestore in production mode.
3. In Authentication, turn on the **Anonymous** and **Google** sign-in providers.
4. Register a **Web** app. Copy its config values into `web/.env.local`
   (see `web/.env.example`).
5. Register an **Android** app with package name `app.kpakam`. Download
   `google-services.json` to `native/mobile/app/`. Do not commit it.
6. Turn on Firebase AI Logic with the Gemini Developer API:
   `npx -y firebase-tools@latest init ailogic`
7. Put the project id in `.firebaserc` (replace `demo-kpakam`).

## 2. Turn on App Check (once)

1. In App Check, register the web app with **reCAPTCHA Enterprise**. Put the site
   key in `VITE_RECAPTCHA_ENTERPRISE_SITE_KEY`.
2. Register the Android app with **Play Integrity**.
3. Enforce App Check for **Firebase AI Logic** and **Cloud Firestore**.
4. For local work, register a debug token:
   1. Start the web app with `npm run dev`.
   2. Copy the token that the browser console prints.
   3. Add it in App Check > Apps > Manage debug tokens.
   4. Optional: put it in `web/.env.local` as `VITE_APPCHECK_DEBUG_TOKEN`.
5. Never commit a debug token. CI reads it from the `APP_CHECK_DEBUG_TOKEN`
   secret.

## 3. Deploy

Deploy the rules **before or with** the app. Older rules reject settings that
the current app writes (for example `voiceLanguage: "auto"`).

1. Run all checks: `npm test && npm run test:rules`.
2. Build the PWA: `npm run build`.
3. Deploy: `npx firebase deploy --only firestore,remoteconfig,hosting`.
4. Open the hosted URL. Check that the Today screen loads and an entry saves.

The manual GitHub workflow **Deploy** does steps 2 and 3. It needs the secrets
listed at the top of `.github/workflows/deploy.yml`.

## 4. Change behaviour without a release (Remote Config)

| Key                           | What it changes                                            | Default                 |
| ----------------------------- | ---------------------------------------------------------- | ----------------------- |
| `cloud_enabled`               | `false` stops all cloud AI calls (kill switch). Jobs wait. | `true`                  |
| `cloud_model_name`            | The cloud Gemini model                                     | `gemini-3.5-flash-lite` |
| `route_language_threshold`    | How much Pidgin/Yoruba sends a note to the cloud           | `0.35`                  |
| `route_max_device_chars`      | Longest note read on the phone                             | `280`                   |
| `route_min_battery`           | Battery level below which the cloud is preferred           | `0.15`                  |
| `cloud_rate_limit_per_minute` | Cloud calls per trader per minute                          | `10`                    |
| `insights_enabled`            | Shows or hides the weekly report                           | `true`                  |
| `prompt_version_parse`        | The parse prompt version that is live (see section 6)      | `parse-v1`              |

The apps fetch Remote Config at most once an hour. The bundled defaults in
`packages/contract/remote-config.defaults.json` apply until the first fetch.

## 5. When something goes wrong

### Cloud costs rise fast

1. Set `cloud_enabled` to `false` in Remote Config.
2. Check the `aiEvents` collection for the `reason` codes that send work to
   the cloud.
3. Raise `route_language_threshold` or lower `cloud_rate_limit_per_minute`.
4. Set `cloud_enabled` back to `true`.

### Writes fail with "permission denied"

1. Compare the deployed rules with `firebase/firestore.rules`.
2. Deploy the rules: `npx firebase deploy --only firestore:rules`.
3. Run `npm run test:rules` to confirm the rules accept the app's writes.

### A customer balance looks wrong

The balance on the customer document is a cache. The true balance is the sum of
that customer's `receivable:<id>` journal lines. No automatic repair job exists
yet. To repair by hand:

1. Export the trader's records (Settings > Download my records).
2. Sum the receivable lines for the customer.
3. Correct `balanceKobo` with the Firebase console or the Admin SDK.

### The phone model never becomes ready (web)

1. Use Chrome on desktop, version 139 or later.
2. Turn on `chrome://flags/#prompt-api-for-gemini-nano-multimodal-input`.
3. Restart Chrome. In the console, run `await LanguageModel.availability()`.
4. If it says `downloadable`, tap the note box in Kpakam once. The download
   starts after a tap (Chrome requires a user action).

## 6. Evaluate a new prompt

1. Copy `packages/contract/prompts/parse-v1.md` to a new version file.
2. Run the cloud eval: `npm run eval:cloud -w @kpakam/web -- --set all`.
3. Run the on-device eval in Chrome at `/dev/eval` (dev builds only).
4. Ship the new version only if neither set loses accuracy.
5. Today each app bundles one parse prompt (`parse-v1`). To switch versions,
   bundle the new file in a release first; `prompt_version_parse` records which
   version is live, and the eval script reads it.
