# Study Audio Practice — Design

## Goal

Make vocabulary audio a deliberate part of the card instead of a browser-only
side effect, then reuse that reference audio for persistent pronunciation
history, shadowing, and dictation. Generated speech may be stored; microphone
recordings are stored only after a successful assessed attempt and remain
private to the authenticated single-user application.

## Decisions

### D1 — Browser speech is the free fallback; applied audio overrides it

Vocabulary `Listen` uses `window.speechSynthesis` when the card has no valid
audio binding. An explicitly applied word/example speech asset overrides that
fallback. A binding is valid only while its saved source text exactly matches
the current card front or example; edited text falls back without playing stale
audio. Production prompts still hide target audio until reveal.

### D2 — Audio is an explicit background enrichment field

`AUDIO` joins the existing enrichment picker. Selecting it is inert until
`Generate selected`. The in-API background job synthesizes the card front and,
when present after text enrichment, its example. Preview audio remains in the
expiring process-local job. Apply persists content-addressed speech assets and
adds source-checked bindings to card metadata; Discard, expiry, or restart
stores no preview bytes. The configured `TEXT_TO_SPEECH` route supplies the
gateway/voice while the card language supplies `en-US` or `zh-CN`.

### D3 — Generated speech and learner recordings are different assets

Generated MP3 remains in the provider-neutral `speech_asset` cache and is
served by the existing authenticated speech endpoint. Learner recordings are
16-kHz, 16-bit mono PCM WAV bytes owned by an audio-practice attempt, not a
speech asset and never a TTS cache entry. Northstar remains single-user and
does not add application-level audio encryption in this increment; existing
session authentication still protects every playback endpoint.

### D4 — Successful attempts persist; retention is bounded by learning value

Pressing record alone never writes. After a successful Word or Shadowing
assessment, Northstar stores the WAV, reference text, duration, provider id and
revision, native Accuracy/Fluency/Prosody, recognized text, and word/phoneme
detail. Dictation stores the typed answer and deterministic diff without a
microphone recording.

Attempt facts remain indefinitely. Recording bytes default to 180 days and at
most 20 recordings per card and practice mode. Pinned recordings plus the
first, best, and latest recording for each card/mode are retained. Cleanup is
lazy on attempt writes/reads so no worker job is required. A learner can pin or
delete an attempt explicitly.

### D5 — One history owns Word, Shadowing, and Dictation

- `WORD` uses the immutable current card front as Azure reference text.
- `SHADOWING` uses the current saved example, falling back to the card front.
  The learner hears the reference, records a repetition, and receives the same
  provider-native delivery evidence as Word practice.
- `DICTATION` uses that same example-or-front reference, hides it, plays the
  applied audio or browser fallback, and compares a typed answer with a
  deterministic case/punctuation-tolerant word diff. LLM grading is unnecessary.

History is newest-first. Trends group by mode and provider because different
vendors' 0–100 scales are not assumed comparable.

### D6 — Delivery scores never become IELTS bands

Accuracy, Fluency, Prosody, word, and phoneme values remain provider-native
0–100 measurements with provider identity. They drive pronunciation trends and
feedback only. They are never renamed, rescaled, averaged into, or displayed
as IELTS. Decisions 0019 and 0023 remain in force except that their transient-
audio rule is superseded only for explicitly retained vocabulary attempts.

### D7 — Audio practice does not grade FSRS

FSRS selects due cards and remains learner-owned. Word, Shadowing, Dictation,
semantic answer checks, and provider scores never submit Again/Hard/Good/Easy
or move a schedule. Only the existing explicit rating action changes memory.

### D8 — API orchestrates interactive work; worker stays out

The API already owns the short-lived enrichment executor, TTS route, microphone
upload, and Azure adapter. Audio generation and assessment stay there. Core
Study persists provider-neutral attempt data and deterministic dictation facts;
core never imports Azure types. The worker is reserved for durable/heavy jobs
and gains no interactive audio responsibility.

## Data shape

Card metadata adds optional source-checked bindings:

```json
{
  "frontAudioAssetId": "uuid",
  "frontAudioText": "serendipity",
  "exampleAudioAssetId": "uuid",
  "exampleAudioText": "We met by pure serendipity."
}
```

`vocab_audio_attempt` stores one typed practice attempt. Speech columns and
recording bytes are nullable for Dictation; dictation answer/diff are nullable
for Word/Shadowing. Database checks keep each mode internally consistent.

## UX

- `R`/Listen plays valid applied word audio, otherwise browser speech.
- The answer face exposes example playback when valid example audio exists.
- `Enrich card` adds Audio and previews word/example players before Apply.
- `Practice pronunciation` opens Word, Shadowing, and Dictation modes.
- A fresh recording is playable before submit. Successful speech attempts are
  playable from history while retained.
- History shows recent scores/diffs, provider, retention/pin state, and a small
  same-provider trend without turning color into the only state signal.

## Non-goals

- No automatic generation or playback.
- No realtime conversational voice mode.
- No IELTS conversion or examiner claim.
- No audio compression/transcoding or object-storage backend in V1.
- No worker/durable enrichment queue.
- No FSRS parameter change or automatic rating.

## Gates and verification

1. Migration V46 plus Hibernate validation on a clean PostgreSQL schema.
2. Unit tests pin transient audio preview/persist-on-Apply, source-checked
   bindings, attempt retention, provider separation, and dictation diff.
3. Controller tests pin authenticated audio serving, mode-owned references,
   upload limits, pin/delete, and that assessment stores only after success.
4. Generated OpenAPI and web types match the REST surface.
5. Web tests pin browser fallback, applied-audio override, production reveal,
   explicit-only Audio enrichment, recording playback, and mode transitions.
6. Java compile/core/full tests, web test/typecheck/build, clean-schema boot,
   and a real browser flow are green. A live configured Azure/TTS pass runs once.
7. Consolidate Study spec/test matrix, add a superseding decision, update the
   roadmap, move this increment to completed, and update Northstar App Behavior.
