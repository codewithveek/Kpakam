# Routing contract

Version: `routing-v1`. Both apps implement this algorithm and run every case in
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

Inputs: `voiceLanguage` (`en` \| `pcm_yo`), `localSpeech`
(`available` \| `downloadable` \| `downloading` \| `unavailable`), `online`,
`cloudEnabled`.

1. `voiceLanguage == en && localSpeech == available` → device (`SPEECH_DEVICE`).
   The transcript then goes through the text router.
2. `online && cloudEnabled` → cloud (`SPEECH_CLOUD`). One call transcribes and
   returns drafts.
3. Otherwise → queue (`SPEECH_CLOUD`, `queueReason` = `OFFLINE` or
   `CLOUD_DISABLED`). The clip is saved and sent later.

## Reason codes

| Code                    | Human label (English UI)                 |
| ----------------------- | ---------------------------------------- |
| `IMAGE_INPUT`           | Photo needs the cloud model              |
| `LONG_CONTEXT`          | Weekly report needs the cloud model      |
| `DEVICE_UNAVAILABLE`    | Phone model not ready                    |
| `LANGUAGE`              | Pidgin or Yoruba detected                |
| `TOO_LONG`              | Long note                                |
| `LOW_BATTERY`           | Saving your battery                      |
| `DEFAULT_DEVICE`        | Private and free on this device          |
| `VALIDATION_ESCALATION` | Phone model was unsure                   |
| `ROUTER_FALLBACK`       | Safety net: phone first, cloud if needed |
| `SPEECH_DEVICE`         | Voice heard on this device               |
| `SPEECH_CLOUD`          | Voice sent to the cloud model            |

Queue reasons: `OFFLINE` ("Waiting for network"), `DATA_SAVER` ("Waiting for
you to tap Send now"), `CLOUD_DISABLED` ("Cloud paused"), `RATE_LIMITED`
("Too many requests; will retry") — the last one is set by the job queue, not
by the router.

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
