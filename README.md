# Kpakam

**Kpakam** is a sales and debt book for Nigerian market traders. A trader types
or says one sentence, such as _"sold 3 bags of rice 45k, Mama Bisi owes 10k"_.
Kpakam turns it into correct entries, works without network, and shows who owes
money. "Kpakam" means _exactly right_.

Kpakam is our entry for **#BuildHybridAI 2026**. It runs as a web app in Chrome
and as a native Android app. Both use **Firebase AI Logic** hybrid inference:
Gemini Nano on the phone when it can, Gemini in the cloud when it must.

![Before and after the UI redesign](docs/ui-before-after.png)

## What a trader can do

- Record a sale, an expense, a credit sale, or a repayment in one sentence.
- Speak instead of typing, in English, Pidgin, or Yoruba, even mixed.
- Take a photo of a receipt (web).
- See today's profit, sales, expenses, and debts paid back.
- See **who owes you**, and record a payment.
- Undo or fix an entry. Kpakam keeps the history; nothing is deleted.
- Keep working offline. Entries are sent when the network returns.
- Save data: photos and weekly reports wait until the trader taps **Send now**.

## Where the AI runs, and why

Kpakam decides for each request. The trader sees the place and the reason on
every result (for example _"On your phone · Free, no data used"_).

| Work                                 | Where                             | Why                                    |
| ------------------------------------ | --------------------------------- | -------------------------------------- |
| Read a simple English note           | Phone (Gemini Nano)               | Free, private, works offline           |
| Hear clear English speech            | Phone (Chrome speech recognition) | Free, no data                          |
| Read Pidgin, Yoruba, or mixed notes  | Cloud (Gemini Flash-Lite)         | The small model is not accurate enough |
| Hear Pidgin, Yoruba, or mixed speech | Cloud (Gemini audio)              | Phone speech only knows English        |
| Read a receipt photo                 | Cloud                             | Needs a large model                    |
| Weekly report                        | Cloud, streamed                   | Long input, not urgent                 |
| Short summary of today               | Phone                             | Private, frequent                      |

If the phone's answer fails the schema check twice, Kpakam asks the cloud
instead. If there is no network, the work waits in a queue. The rules are in
[`packages/contract/routing.md`](packages/contract/routing.md), and both apps
run the same test cases.

## For judges: how to see each route

Use **Chrome on desktop, version 139 or later**. To use Gemini Nano:

1. Open `chrome://flags/#prompt-api-for-gemini-nano-multimodal-input`.
2. Set it to **Enabled** and restart Chrome.
3. Open Kpakam and tap the note box once. The phone model starts to download.
   The note box shows the progress.

Then try these:

| To see                | Do this                                                         | Badge shows                              |
| --------------------- | --------------------------------------------------------------- | ---------------------------------------- |
| On the phone, offline | Turn off Wi-Fi. Type `sold 2 tins of milk 2000`.                | On your phone · Free, no data used       |
| Language check        | Type `Mama Nkechi don pay the 3k wey she dey owe`.              | Online · Pidgin or Yoruba heard          |
| Pidgin while offline  | Turn off Wi-Fi and type the Pidgin note.                        | On your phone, with "Please check this"  |
| Phone model missing   | Open Kpakam in a browser without Gemini Nano.                   | Online · Your phone can't read notes yet |
| Save data             | Settings > turn on **Save data**. Add a receipt photo.          | Waiting for you to tap Send now          |
| Mixed speech          | Settings > Language you speak: **Any**. Speak a mixed sentence. | Online · Mixed languages heard           |
| Offline queue         | Turn off Wi-Fi in a browser without Gemini Nano. Type a note.   | Waiting for network                      |

**Hosted app:** add the link here after the first deploy (see
[`docs/runbook.md`](docs/runbook.md)).

## Repository layout

```text
kpakam/
├── web/                 # Web app (React 19, Vite, TypeScript)
├── native/mobile/       # Android app (Kotlin, Jetpack Compose)
├── packages/contract/   # Shared rules: schemas, routing, ledger, prompts, test sets
├── firebase/            # Firestore rules and tests, indexes, Remote Config template
├── docs/                # Decision records and the runbook
└── .github/             # CI, deploy, cloud eval, Dependabot
```

## Run it on your computer

You do not need a Firebase project for this. The app uses local emulators and a
scripted model.

1. Install Node.js 22 and Java 21.
2. Install the packages: `npm ci`
3. Start the emulators:
   `npx firebase emulators:start --only auth,firestore --project demo-kpakam-e2e --config firebase.e2e.json`
4. In a second terminal, start the app: `npm run dev -w @kpakam/web -- --mode e2e`
5. Open `http://localhost:5173`.

To use real Gemini models, connect a Firebase project. Follow
[`docs/runbook.md`](docs/runbook.md), sections 1 and 2.

For Android, see [`native/mobile/README.md`](native/mobile/README.md).

## Check the code

| Check                      | Command                                                                         |
| -------------------------- | ------------------------------------------------------------------------------- |
| Shared contract            | `npm run check:contract`                                                        |
| Types and lint             | `npm run typecheck && npm run lint`                                             |
| Unit and component tests   | `npm test`                                                                      |
| Firestore rules (emulator) | `npm run test:rules`                                                            |
| End-to-end journeys        | `npm run test:e2e -w @kpakam/web`                                               |
| Bundle budgets             | `npm run build && npm run budgets -w @kpakam/web`                               |
| Android                    | `./gradlew :core:test ktlintCheck detekt :app:assembleDebug` in `native/mobile` |

CI runs all of these on every pull request
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

## AI quality

The golden sets hold 60 English and 40 Pidgin/Yoruba sentences with the correct
answers ([`packages/contract/golden`](packages/contract/golden)).

| Set           | Where               | Valid on first try | Correct     |
| ------------- | ------------------- | ------------------ | ----------- |
| English       | Phone (Gemini Nano) | not run yet        | not run yet |
| English       | Cloud               | not run yet        | not run yet |
| Pidgin/Yoruba | Cloud               | not run yet        | not run yet |

Run the cloud eval with `npm run eval:cloud -w @kpakam/web`. Run the phone eval
in Chrome at `/dev/eval` (dev builds only). Put the scores in this table.

## Documentation

- [Decision records](docs/adr/README.md): why the app is built this way.
- [Runbook](docs/runbook.md): set up, deploy, operate.
- [Contract](packages/contract/README.md): the rules both apps share.
- [Firebase](firebase/README.md): security model and rules tests.
- [Web app](web/README.md) and [Android app](native/mobile/README.md).

## License

Apache-2.0. See [LICENSE](LICENSE).
