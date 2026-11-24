# Kpakam for Android

Kpakam is an offline-first sales and debt ledger for Nigerian market traders.
This folder holds the native Android app. It shares its Firestore data and its
contract with the PWA in `web/`.

The contract lives in `packages/contract`. The app reads it at build time. The
tests read it directly. Do not copy contract files into this folder.

## What you need

- JDK 21.
- The Android SDK with platform `android-37.2` and build tools 37.
- A Firebase project with Firestore, Anonymous Auth, Remote Config, App Check,
  and Firebase AI Logic (Gemini Developer API).
- For on-device inference: a phone that supports Gemini Nano through ML Kit
  (AICore). Other phones use the cloud model. The app shows this in a badge.

## Set up the project

1. Open a terminal in `native/mobile`.
2. Tell Gradle where the SDK is. Put this line in `local.properties`:
   `sdk.dir=/path/to/android-sdk`
3. Register an Android app in Firebase with the package name `app.kpakam`.
4. Download `google-services.json` for that app.
5. Put the file at `app/google-services.json`. Git ignores this file. Do not
   commit it.
6. Enable Firebase AI Logic for the project:
   `npx -y firebase-tools@latest init ailogic`
7. Enable Anonymous sign-in in the Firebase console (Authentication).
8. Deploy the Firestore indexes that the queries need (see "Firestore" below).

Without `google-services.json`, the project still builds. The app then shows a
"Firebase is not set up" screen.

## Set up App Check for debug builds

Debug builds use the App Check debug provider. Release builds use Play
Integrity.

1. Run a debug build on a phone or an emulator.
2. Find the line `DebugAppCheckProvider: Enter this debug secret` in Logcat.
3. Copy the token.
4. In the Firebase console, open App Check, then Apps, then Manage debug tokens.
5. Add the token.
6. To keep the same token after a reinstall, add it to `local.properties`:
   `APP_CHECK_DEBUG_TOKEN=your-token`
7. Never commit a debug token. `local.properties` is ignored by Git.

## Build and run

1. Build the debug app: `./gradlew :app:assembleDebug`
2. Install it: `./gradlew :app:installDebug`

## Run the tests

1. Run the core tests: `./gradlew :core:test`
2. Run the app unit tests: `./gradlew :app:testDebugUnitTest`
3. Run the style checks: `./gradlew ktlintCheck detekt`
4. To fix formatting, run: `./gradlew ktlintFormat`

The core tests run every case in `routing-cases.json` (text, speech, and
`speechCheck`), `journal-cases.json`,
`language-cases.json`, and every line of the golden sets. Gradle passes the
contract folder as the `contractDir` system property. To use another folder,
add `-PcontractDir=/path/to/contract`.

`:core` builds on a plain JVM. If Gradle finds no Android SDK, it includes only
`:core` and prints a message. `./gradlew :core:test` still works.

## Module layout

| Path | What it holds |
| --- | --- |
| `core/` | Pure Kotlin. No Android. Money (`Kobo`), ids (`Ulid`, `CustomerId`), draft validation, journal building and reversal, invariants, balances, day totals, the text and speech routers, escalation, the language score, prompt assembly, and the AI job state machine. |
| `app/` | The Android app: Jetpack Compose, Material 3, Hilt, Room, WorkManager, Firestore, Firebase AI Logic. |
| `app/.../features/` | `quickentry`, `ledger`, `customers`, `dashboard`, `settings`. Each has its screen, ViewModel, use cases, and repository. |
| `app/.../inference/` | The router adapter, `InferenceClient` and its device, cloud, and safety-net adapters, the model runner (validation, retry, escalation), and the job queue (Room, WorkManager). |
| `app/.../platform/` | Thin adapters: Firebase (App Check, Auth, Remote Config, paths), network, battery, speech, contract assets. |
| `app/.../shared/` | UI kit, naira formatting, Africa/Lagos dates. |
| `config/detekt/` | The detekt rules. |
| `gradle/libs.versions.toml` | All versions. |

Code flows in one direction: Composable, ViewModel, use case or service,
repository, platform adapter, SDK. Services do not import Firebase types.

## How the build uses the contract

- `generateRemoteConfigDefaults` turns `remote-config.defaults.json` into
  `res/xml/remote_config_defaults.xml`.
- `copyContractAssets` copies the prompts, `draft.schema.json`,
  `language-lexicon.json`, and the Remote Config defaults into
  `assets/contract`.
- The Firebase AI `responseSchema` is built from `draft.schema.json` at run time.

## Hybrid inference

- The app owns the routing decision (`routing.md`). Each model is fixed to one
  place: `ONLY_ON_DEVICE` or `ONLY_IN_CLOUD`.
- Invalid device output is retried once on the device with the errors. Then it
  goes to the cloud (`VALIDATION_ESCALATION`) or to the queue.
- If reading the signals or running the router throws, the app makes one call
  with a `PREFER_ON_DEVICE` model (`ROUTER_FALLBACK`).
- Voice language is "Any" (`auto`) by default. The trader can also pick English, or
  Pidgin or Yoruba, in Settings.
- In "Any" mode on Android 13 and later, the phone listens with its on-device
  recognizer. The app captures the microphone itself and gives the same audio to the
  recognizer, so it keeps a compressed clip (AAC) at the same time.
- After the phone listens, the app runs the mixed-speech check (`routing.md`). If the
  words cannot be trusted, the clip goes to the cloud model (`SPEECH_MIXED`). Offline,
  the clip waits in the queue. Otherwise the words go into the text box.
- Before Android 13 the microphone cannot be shared, so "Any" mode records the clip
  and sends it to the cloud model (`SPEECH_CLOUD`).
- English mode listens on the phone and skips the check. Pidgin or Yoruba mode
  records Opus in Ogg (Android 10 and later) or AAC, and sends it to the cloud model
  with the `voice-v1` prompt.
- The cloud model name comes from Remote Config `cloud_model_name`.

## Firestore

All data is under `users/{uid}`. Journals are created, never changed or
deleted. A correction is a reversal journal.

These queries need composite indexes:

| Collection | Fields |
| --- | --- |
| `journals` | `day` ascending, `occurredAt` descending |
| `journals` | `customerId` ascending, `occurredAt` descending |
