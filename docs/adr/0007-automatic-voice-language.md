# 0007. The app works out the voice language

## Status

Accepted, 23 November 2026. Replaces the PRD rule "the app does not guess from
audio".

## Context

Traders mix English, Pidgin, and Yoruba in one sentence. Asking them to pick a
language first is slow, and the choice is often wrong. The phone's speech
recogniser only knows English. The cloud model understands all three, mixed.

## Decision

- The voice language setting defaults to `auto` ("Any"). `en` and `pcm_yo`
  remain as overrides.
- In `auto` mode, when the phone can recognise speech, it listens for free and
  records the clip at the same time.
- After listening, a mixed-speech check runs: the transcript is empty, or its
  Pidgin/Yoruba score is above `route_language_threshold`, or the recogniser's
  confidence is below 0.5. Then the clip goes to the cloud (`SPEECH_MIXED`), or
  waits offline. Otherwise the phone's words go into the text box.
- Without a phone recogniser, the clip goes to the cloud (`SPEECH_CLOUD`).

## Consequences

- Clear English stays free and offline. Mixed speech is read correctly.
- The app records audio in parallel with recognition. Some browsers may not
  allow both; then the phone's words are used without the backup clip.
- Android 13+ shares one microphone stream (`EXTRA_AUDIO_SOURCE`). Older
  Android records first and always reads the clip in the cloud.
- Firestore rules accept `voiceLanguage: "auto"`. Deploy the rules with the app.
