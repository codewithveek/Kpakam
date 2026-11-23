# 0002. No custom server: apps talk to Firebase directly

## Status

Accepted, 23 November 2026.

## Context

The hackathon allows 7 days. A custom server adds deployment, secrets,
scaling, and a new attack surface. Firebase AI Logic is designed to be called
from client apps, protected by App Check.

## Decision

- The PWA and the Android app call Firestore, Auth, Remote Config, and Firebase
  AI Logic through the client SDKs.
- App Check protects every call: reCAPTCHA Enterprise on the web, Play Integrity
  on Android, debug tokens only in local and CI builds.
- Security lives in `firebase/firestore.rules`: owner-only paths, field
  validation, the zero-sum check on journal lines, and create-only journals.

## Consequences

- No server to deploy or patch. Offline writes work through the Firestore
  cache.
- The client is untrusted, so every money rule that matters is enforced twice:
  in domain code and in the rules. The rules have 196 emulator tests.
- Some work is harder without a server: rate limits are per client, and a
  cached customer balance can only be checked against journals by a repair job.
