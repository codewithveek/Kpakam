# Routing contract

Version: `routing-v2`. Both apps implement this algorithm and run every case in
`routing-cases.json`. If the algorithm changes, change this file, the cases,
and both implementations in the same pull request.

## Text and image router

Inputs (`signals`):

| Field           | Type                                                            | Meaning                                          |
| --------------- | --------------------------------------------------------------- | ------------------------------------------------ |
| `task`          | `parse_text` \| `parse_image` \| `summary` \| `insights`        | What the job does                                |
| `chars`         | integer                                                         | Length of the text input (0 for images)          |
| `languageScore` | number 0–1                                                      | Pidgin/Yoruba score from `language-lexicon.json` |
| `online`        | boolean                                                         | Device has a working connection                  |
| `cloudEnabled`  | boolean                                                         | Remote Config `cloud_enabled` kill switch        |
| `dataSaver`     | boolean                                                         | Trader's data-saver switch                       |
| `approved`      | boolean                                                         | Trader tapped "Send now" for this job            |
| `deviceModel`   | `available` \| `downloadable` \| `downloading` \| `unavailable` | On-device model state                            |
| `battery`       | `{ level, charging }` \| `null`                                 | `null` when the Battery API is missing           |

Config: `route_max_device_chars`, `route_language_threshold`,
`route_min_battery` from Remote Config. Cases use the bundled defaults unless
a case gives `config`.

Derived values:

- `cloudReachable = online && cloudEnabled`
- `cloudBlockedReason = online ? "CLOUD_DISABLED" : "OFFLINE"`
- `deviceReady = deviceModel == "available"`

Checks run in order. The first match wins.

1. `task == parse_image` → `IMAGE_INPUT`.
   Not `cloudReachable` → queue (`cloudBlockedReason`).
   `dataSaver && !approved` → queue (`DATA_SAVER`). Else → cloud.
2. `task == insights` → `LONG_CONTEXT`. Same queue rules as step 1.
3. `!deviceReady` → `DEVICE_UNAVAILABLE`.
   `cloudReachable` → cloud. Else → queue (`cloudBlockedReason`).
4. `languageScore > route_language_threshold` → `LANGUAGE`.
   `cloudReachable` → cloud. Else → device with `confidence: low`.
5. `chars > route_max_device_chars` → `TOO_LONG`.
   `cloudReachable` → cloud. Else → device with `confidence: low`.
6. `battery != null && !battery.charging && battery.level < route_min_battery && cloudReachable`
   → cloud (`LOW_BATTERY`).
7. Otherwise → device (`DEFAULT_DEVICE`).

Output (`decision`): `{ target, reason, confidence, queueReason }`.
`target` is `device`, `cloud`, or `queue`. `confidence` is `normal` unless a
rule says `low`. `queueReason` is `null` unless `target == queue`.

Data-saver holds only large jobs (steps 1 and 2). Text parsing is a few KB and
always runs on mobile data.

## Escalation (not part of the pure router)

After a device run, the output is validated. Invalid → retry once on device
with the errors in the prompt. Invalid again → cloud (`VALIDATION_ESCALATION`)
if `cloudReachable`, else queue (`cloudBlockedReason`). A queued job is routed
again when it drains.

## Safety net (not part of the pure router)

If reading the signals or running the router throws (a bug, or a platform API
that misbehaves), the app makes one call with a model created in
`PREFER_ON_DEVICE` mode (`ROUTER_FALLBACK`). The output is validated like any
other. The response's `inferenceSource` says where it ran.

## Speech router

The trader does not have to pick a language. The default, `auto`, lets Kpakam
work it out, even when one sentence mixes English, Pidgin and Yoruba.

Inputs: `voiceLanguage` (`auto` \| `en` \| `pcm_yo`, default `auto`),
`localSpeech` (`available` \| `downloadable` \| `downloading` \| `unavailable`),
`online`, `cloudEnabled`, `mixedHeard` (boolean, default `false`; see below).

1. `mixedHeard` → `SPEECH_MIXED`. `online && cloudEnabled` → cloud. Else →
   queue (`OFFLINE` or `CLOUD_DISABLED`); the clip is read when it can be.
2. `voiceLanguage` is `auto` or `en`, and `localSpeech == available` → device
   (`SPEECH_DEVICE`). The phone listens; in `auto` mode it also records the
   clip at the same time, in case the check below fails.
3. `online && cloudEnabled` → cloud (`SPEECH_CLOUD`). One call writes down what
   was said (in any of the three languages, mixed or not) and returns drafts.
4. Otherwise → queue (`SPEECH_CLOUD`, `queueReason` = `OFFLINE` or
   `CLOUD_DISABLED`). The clip is saved and sent later.

### Mixed-speech check (auto mode only)

The phone's speech recogniser only knows English. After it finishes, the app
decides whether its words can be trusted. `mixedHeard` is true when any of:

- the transcript is empty after trimming;
- `languageScore(transcript) > route_language_threshold` (Pidgin or Yoruba
  words heard);
- the recogniser reported a confidence and it is below `0.5`.

If `mixedHeard` and a clip was recorded, route again with `mixedHeard = true`
(step 1): the clip goes to the cloud model, which understands mixed speech.
Otherwise the transcript is used, and goes through the text router like typed
text. In `en` mode the check is skipped (the trader chose English).
`speechCheck` in `routing-cases.json` holds the shared test cases.

## Reason codes

Labels are plain words for traders: no "device", "cloud" or "model".

| Code                    | Label shown to the trader       |
| ----------------------- | ------------------------------- |
| `IMAGE_INPUT`           | Photos are read online          |
| `LONG_CONTEXT`          | Weekly report is made online    |
| `DEVICE_UNAVAILABLE`    | Your phone can't read notes yet |
| `LANGUAGE`              | Pidgin or Yoruba heard          |
| `TOO_LONG`              | Long note                       |
| `LOW_BATTERY`           | Saving your battery             |
| `DEFAULT_DEVICE`        | Free, no data used              |
| `VALIDATION_ESCALATION` | Checked again online            |
| `ROUTER_FALLBACK`       | Read the best way available     |
| `SPEECH_DEVICE`         | Heard on your phone             |
| `SPEECH_CLOUD`          | Voice read online               |
| `SPEECH_MIXED`          | Mixed languages heard           |

Queue reasons: `OFFLINE` ("Waiting for network"), `DATA_SAVER` ("Waiting for
you to tap Send now"), `CLOUD_DISABLED` ("Online reading is paused"),
`RATE_LIMITED` ("Busy, will try again soon") — the last one is set by the job
queue, not by the router.

## Language score

`language-lexicon.json` lists marker words with weights. Algorithm:

1. Lower-case the text and normalise it to Unicode NFC. Count Yoruba diacritic characters
   (`ẹ ọ ṣ à á è é ì í ò ó ù ú ń`) as `diacritics`.
2. Split into tokens on any run of characters that are not letters or
   apostrophes. Drop empty tokens.
3. `hits` = sum of weights of tokens found in `markers`, plus the weight of
   each phrase in `phrases` that appears as whole words in the tokens joined
   by single spaces (each phrase counts once).
4. `score = min(1, (hits + 0.5 * diacritics) / max(3, tokenCount) * 2)`.
5. Round to 2 decimals.

`language-cases.json` holds texts with expected scores; both apps test them.
