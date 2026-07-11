# 0019 - Speech Assessment Separates Azure Delivery From LLM Content

## Status

Accepted. Supersedes the REST-only transport and Azure content-score portions
of D7 in the speech-assessment increment design.

## Context

The increment originally selected Azure's short-audio REST endpoint for both
scripted vocabulary reads and unscripted speaking answers. Microsoft's current
documentation, updated 2026-06-05, limits REST pronunciation assessment to 30
seconds and requires `ReferenceText`. The product contract allows a 60-second
unscripted answer. Microsoft's Speech SDK supports unscripted assessment with
an empty reference and continuous recognition for longer, multi-utterance
audio.

Azure also retired Content Assessment in Speech SDK 1.46 and later. Its current
guidance is to use a chat model for vocabulary, grammar, and topic relevance.
Treating retired content fields as an integration contract would make the
feature depend on undocumented behavior.

## Decision

- `integrations/speech-azure` uses Microsoft Speech SDK 1.50.0 for both reading
  and speaking, keeping one provider transport. Reading uses one-shot scripted
  assessment; speaking uses continuous unscripted assessment.
- The core contract carries stable provider identity and revision. Delivery
  configuration selects one adapter by `northstar.speech.provider`; future
  providers live in sibling `integrations/speech-*` modules. Persisted attempts
  keep provider identity because 0-100 scales from different vendors are not
  assumed comparable.
- Azure remains authoritative only for measured delivery: transcript,
  pronunciation accuracy, fluency, prosody, and word/phoneme detail.
- `SpeakingCoach` uses Northstar's pinned study-grader route to assess
  vocabulary, grammar, and topic relevance from the transcript and question.
  It may use Azure results to prioritize coaching prose, but never changes the
  measured scores, combines them into a new overall score, or maps them to an
  IELTS band.
- The web presents "Azure delivery - unofficial" and "AI content feedback -
  unofficial" as separate groups.
- WAV audio is validated and decoded in memory, then its header-free 16-kHz
  mono PCM payload is passed through `PushAudioInputStream`. Audio is never
  persisted.
- Linux API images install the Speech SDK native runtime prerequisites. Versions
  before 1.48.2 are forbidden because Microsoft's July 2026 CRL compatibility
  change can break Linux connections.

## Consequences

The adapter gains a native dependency and continuous-recognition lifecycle,
but the 60-second product contract is supported by a documented API. Unit tests
cover WAV validation, response parsing, and paragraph aggregation through a
provider seam; a live dev-only call covers native/service wiring. Content
feedback keeps the same evaluator-optimizer and personal-error-corpus controls
as writing, while delivery scores remain factual Azure output rather than LLM
inference.
