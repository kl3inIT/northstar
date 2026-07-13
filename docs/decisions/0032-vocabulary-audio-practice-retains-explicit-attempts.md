# 0032 - Vocabulary Audio Practice Retains Explicit Attempts

## Status

Accepted. Supersedes only the blanket audio-never-persists clauses in decisions
0019 and 0023 for explicit vocabulary Word and Shadowing attempts. Speaking
practice audio remains transient.

## Context

Browser text-to-speech makes a card immediately usable, but its voice varies by
device and cannot supply a stable reference for Shadowing or Dictation. The
existing provider-routed speech cache can create a deliberate reference without
automatic cost. Vocabulary pronunciation was also live-only, so the learner
could neither replay an attempt nor see whether provider-native delivery
measurements improved over time.

This is a private single-user application. Retaining every microphone recording
forever is still unnecessary; retaining selected attempts long enough to replay
and compare is useful. Provider scores remain vendor measurements, not an IELTS
scale, and audio practice must not silently grade memory.

## Decision

- `window.speechSynthesis` remains the zero-setup default. Audio is generated
  only after the learner selects `AUDIO`, starts enrichment, reviews its
  transient preview, and applies it. Applied speech is content-addressed and
  valid only while its bound card text is unchanged.
- Word practice assesses the card front. Shadowing and Dictation use the saved
  example, falling back to the front. Dictation uses deterministic normalized
  token diffing rather than an LLM.
- Successful Word and Shadowing assessments persist provider-neutral facts and
  the submitted 16-kHz mono PCM WAV. Recording alone never writes. Dictation
  persists facts without microphone audio.
- Attempt facts remain. Recording bytes expire after 180 days and are capped at
  20 per card/mode; pinned, first, best, and latest recordings are preserved.
  Cleanup is lazy on reads/writes, so interactive audio stays in the API and no
  worker is introduced.
- Every playback endpoint remains authenticated. Northstar does not add
  application-level recording encryption for its single-user deployment.
- Accuracy, Fluency, Prosody, words, and phonemes retain provider identity and
  native 0-100 values. They are never renamed, converted, or shown as IELTS.
- Word, Shadowing, Dictation, semantic checking, and enrichment never alter
  FSRS. Only an explicit Again/Hard/Good/Easy rating changes scheduling.

## Consequences

The learner gains consistent optional reference audio, replayable attempts, and
useful trends without making paid generation automatic. Database size is
bounded by retention and selection value. The API owns short interactive work;
object storage, durable job delivery, and cross-provider score normalization
remain future concerns.
