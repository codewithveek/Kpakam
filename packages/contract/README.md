# @kpakam/contract

This package is the single source of truth that the PWA (`web/`) and the
Android app (`native/mobile/`) share. If you change a file here, update both
apps in the same pull request. CI runs both apps' tests against these files.

| File                                           | What it defines                                                                                |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `schemas/draft.schema.json`                    | What a model must return for a note                                                            |
| `schemas/journal.schema.json`                  | The stored journal document                                                                    |
| `ledger.md`, `journal-cases.json`              | How a draft becomes journal lines                                                              |
| `routing.md`, `routing-cases.json`             | Where each AI job runs, and why                                                                |
| `language-lexicon.json`, `language-cases.json` | The Pidgin/Yoruba score                                                                        |
| `prompts/*.md`                                 | Versioned prompts and how to assemble them                                                     |
| `remote-config.defaults.json`                  | Bundled Remote Config defaults                                                                 |
| `golden/en.jsonl`, `golden/pcm.jsonl`          | Evaluation sets with expected drafts (see `golden/README.md`)                                  |
| `scripts/score-golden.mjs`                     | `scoreGolden()`: scores one model output against a golden line (pure; runs in Node and Chrome) |
| `scripts/draft-validator.mjs`                  | Ajv validator for the draft schema (Node)                                                      |

## Check the contract

1. Run `npm run check -w @kpakam/contract`.
2. The script validates the JSON files and the golden sets against the schemas,
   checks the routing codes and Remote Config types, recomputes the language
   scores, checks the prompt headers, and self-tests `scoreGolden()`.
3. A non-zero exit code means a check failed. The output lists each problem.
