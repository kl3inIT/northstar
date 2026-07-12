# IELTS Speaking Estimate V1 — Design

## Goal

Turn the existing one-question Speaking practice result into a useful,
explicitly unofficial IELTS-style estimate without relabelling Azure's 0-100
delivery measurements as IELTS bands.

The learner receives four criterion ranges — Fluency and Coherence, Lexical
Resource, Grammatical Range and Accuracy, and Pronunciation — plus a
deterministically aggregated overall range. Every estimate carries evidence,
an overall LOW confidence for the one-answer sample, and a scorer version.

## Decisions

### E1 — Composite rubric assessment, never Azure-score conversion

Azure remains authoritative only for transcript, pronunciation, fluency,
prosody, and word/phoneme delivery evidence. The study grader receives that
evidence together with the question, transcript, recording duration, word
count, and speech rate. It assesses the four IELTS-style criteria against a
rubric resource; no formula maps an individual Azure value to a band.

### E2 — Ranges and evidence, not false point precision

Each criterion has `minBand` and `maxBand` on half-band steps from 1 to 9,
never wider than 1.0. FC/LR/GRA justifications must quote the transcript
verbatim. Pronunciation justification must name measured delivery values or
low-accuracy words and must not claim the LLM heard qualities absent from the
evidence envelope.

The overall range is calculated in Java from the four criterion bounds using
equal weighting and half-band rounding. The LLM never supplies overall bands.

### E3 — One answer means LOW overall confidence

This increment extends the existing one-question practice flow rather than
pretending it is a full examiner session. The stored estimate is labelled
`Unofficial one-answer IELTS-style estimate` and overall confidence is always
`LOW`. A future full Part 1-3 increment can introduce session aggregation and
revisit confidence only with examiner-labelled evaluation data.

### E4 — Keep the existing content coach and safety loop

One structured grader call returns the existing 0-100 vocabulary, grammar,
and topic practice scores, top errors, summary, and the four band ranges.
Structural validation checks exact criteria, half-band ranges, evidence,
confidence, quotes, and score bounds. The existing faithfulness evaluator and
single corrective retry remain mandatory.

### E5 — Persist an auditable snapshot, never audio

`speaking_feedback` gains `ielts_estimate` JSON text and `estimate_version`.
Existing rows are backfilled as unavailable legacy estimates. New rows store
the complete criterion ranges/evidence, deterministic overall range, label,
confidence, and version. Audio continues to be decoded only in memory and is
never stored.

## Contract

Persisted estimate JSON:

```json
{
  "criteria": [
    {
      "key": "FC",
      "minBand": 5.5,
      "maxBand": 6.0,
      "confidence": "MEDIUM",
      "justification": "..."
    }
  ],
  "overallMin": 5.5,
  "overallMax": 6.0,
  "confidence": "LOW",
  "label": "Unofficial one-answer IELTS-style estimate"
}
```

The API exposes the JSON and version with each speaking-feedback row. The web
parses it leniently so legacy or malformed rows show no estimate rather than
breaking history.

## Out of scope

- Direct Azure-to-IELTS lookup tables.
- Official-score claims or high-stakes use.
- Full Part 1-3 sessions, realtime voice, interruptions, or TTS.
- Import pipelines, new speech providers, or per-card history.
- A claim of examiner agreement before a labelled calibration set exists.

## Verification

- Pure tests for criterion/range/quote validation and overall aggregation.
- Existing evaluator evidence includes question, transcript, duration, speech
  rate, Azure delivery values, and low-accuracy words.
- Flyway/JPA validation through the terminating context-load gate.
- OpenAPI/client regeneration and web typecheck/build.
- Browser walkthrough of current attempt and legacy history rendering.
- One live Azure + study-grader attempt on this machine; no live call in CI.

