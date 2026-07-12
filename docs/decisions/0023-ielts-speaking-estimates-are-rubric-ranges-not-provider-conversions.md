# 0023 - IELTS Speaking Estimates Are Rubric Ranges, Not Provider Conversions

## Status

Accepted. Supersedes only the blanket prohibition on any speaking-band
estimate in decision 0019 and D2 of the completed speech-assessment design.
The prohibition on mapping or presenting provider scores as IELTS bands
remains in force.

## Context

Azure returns useful 0-100 delivery measurements but does not define an IELTS
conversion. At the same time, raw provider values are difficult for a learner
to relate to the four public IELTS Speaking criteria. Products can bridge that
gap only by adding a separate rubric assessment over acoustic evidence,
transcript evidence, and task context; the resulting number belongs to the
product's estimator, not to Azure or IELTS.

Northstar already separates Azure delivery from evaluator-checked LLM content
feedback and stores provider/model revisions. That boundary can support a
transparent practice estimate without corrupting the measured data, provided
the product exposes uncertainty and never claims official examiner precision.

## Decision

- Azure/provider measurements stay on their native scale and retain their
  provider identity. They are never renamed, rescaled, or displayed as IELTS.
- A separate study-grader assessment may estimate FC, LR, GRA, and P from the
  question, transcript, timing, word-level delivery, and provider scores.
- Every criterion is a half-band range with checkable evidence. The one-answer
  overall estimate is an equal-weight deterministic aggregation and always
  carries LOW confidence.
- UI and API call the result an `Unofficial one-answer IELTS-style estimate`.
  It is practice feedback, not an IELTS result or an examiner substitute.
- Scorer versions are persisted. Claims of accuracy or higher confidence
  require a future examiner-labelled calibration evaluation and a new
  decision.
- Audio remains transient and is never persisted.

## Consequences

The learner gains a familiar rubric-oriented view while raw delivery remains
auditable. The extra structured-output surface and validation increase grader
complexity and token cost. A single answer cannot represent a full Speaking
test, so this version deliberately favors ranges and low confidence; full-test
aggregation and empirical calibration remain future work.

