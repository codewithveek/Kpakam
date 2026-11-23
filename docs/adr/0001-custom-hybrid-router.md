# 0001. Kpakam picks device or cloud for each request

## Status

Accepted, 23 November 2026.

## Context

Firebase AI Logic offers `PREFER_ON_DEVICE`, which tries Gemini Nano and falls
back to the cloud. That choice is made inside the SDK. We cannot explain it to
the trader, test it, or apply our own rules (language, data use, battery).

Some work must not run on the device at all: Pidgin and Yoruba parsing, photos,
and long reports. Some work must not use mobile data unless the trader agrees.

## Decision

- A pure function, `routeJob(signals, config)`, picks `device`, `cloud`, or
  `queue` for each request, with a reason code. The rules are in
  `packages/contract/routing.md`.
- Each call uses a model created with `ONLY_ON_DEVICE` or `ONLY_IN_CLOUD`, so the
  SDK never overrides the choice.
- Every device output is validated against the draft schema. An invalid output
  is retried once on device with the errors in the prompt, then sent to the
  cloud (`VALIDATION_ESCALATION`), or queued when the cloud is not reachable.
- A `PREFER_ON_DEVICE` model remains only as a safety net when routing itself
  throws (`ROUTER_FALLBACK`).
- The thresholds (`route_max_device_chars`, `route_language_threshold`,
  `route_min_battery`) live in Remote Config, with bundled defaults.

## Consequences

- Each decision is visible to the trader (a badge with a plain-words reason)
  and stored in `aiEvents` for the on-device share metric.
- Both apps run the same `routing-cases.json`, so the rules cannot drift.
- We maintain more code than with `PREFER_ON_DEVICE`, and we must update the
  router when the SDK adds capabilities.
