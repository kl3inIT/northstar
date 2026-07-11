# 0016 - Writing Grader Uses Sourced, Anchored, Comparative Grading

## Status

Accepted.

## Context

The writing tutor's first rubric was hand-condensed band descriptors with
invented exemplar excerpts. Research into open-source graders, the AES
literature, and production systems showed that every load-bearing part of a
grading prompt has a sourced, measured alternative:

- GPT-4-class models zero-shot fail to beat a length-only baseline; one
  scored calibration example per rating category lifted agreement to QWK
  0.81 versus a production engine's 0.84, and detailed rubric text plus
  forced rationale added nothing once exemplars were present (Yancey et al.,
  BEA 2023).
- Comparative judgment against anchor essays beat absolute rubric scoring by
  +0.11..0.21 QWK, surpassing human raters on ASAP 7-8 (Kim & Jo 2024),
  while analytic per-criterion scoring is the weakest LLM mode (QWK
  0.32-0.41 vs ~0.60 holistic).
- GPT-4 matched official IELTS examiner scores on average (weighted kappa
  0.811, no systematic bias — Koraishi 2024), but 29% of individual essays
  landed more than half a band off, and model-version drift moved QWK by
  0.16 at a fixed prompt (Yoshida 2024).
- No high-stakes production system trusts a single automated score: ETS uses
  e-rater only as a check score with human escalation above ±0.5; error
  feedback and holistic scoring are separate models everywhere examined.

## Decision

- The rubric resource embeds the official public IELTS band descriptors
  verbatim (bands 1-9, TR/TA/CC/LR/GRA) and five officially-scored anchor
  essays (bands 4-8), with provenance noted in the file and a rule against
  paraphrasing. Domain-critical prompt content is sourced, never invented.
- Grading is comparative and holistic-first: the model places the essay
  between anchors, that placement decides the overall estimate range, and
  per-criterion bands are diagnostic feedback that is never averaged into
  the overall.
- The output is an estimate range (half-band steps, at most 1.0 wide),
  explicitly framed as unofficial — the 29%-beyond-half-band finding is the
  justification.
- The grader model id is pinned by configuration and stored on every
  feedback row, because estimates from different model versions are not
  comparable.
- Every grading runs an evaluator-optimizer loop (the Spring AI agentic
  pattern, bounded at two attempts): structural checks in plain code, then
  a faithfulness check through a Spring AI `Evaluator` implementation
  modeled on `FactCheckingEvaluator`; failures feed one corrective re-grade.
- Error patterns accumulate into a corpus injected into later gradings, so
  feedback tracks the learner, not just the essay. Reasoning-first output
  order stays for feedback quality, not score quality.

## Consequences

Band estimates are as calibrated as the current evidence allows without
fine-tuning, and their trustworthiness is documented rather than implied.
The prompt is large (descriptors + five anchors) and every grading costs an
extra evaluator call, acceptable for a single-user app. Anchors are Task 2
only, so Task 1 grading leans on descriptors alone until scored Task 1
anchors are added. A rubric for a second subject is a new prompt resource
plus a rubric key — no schema change. Self-consistency multi-sampling was
considered and skipped: published gains are small and the range already
carries the uncertainty.
