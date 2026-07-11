# 0015 - Vocab Memory Uses Recall Probability, Not Due Dates

## Status

Accepted.

## Context

The study module needs spaced repetition for IELTS/HSK vocabulary, but the
user does not follow daily rituals and reviews must happen inside chat and
the brief, not a flashcard app. The canonical SRS failure mode is the
post-lapse backlog: due-date schedulers (SM-2, FSRS) accumulate a debt of
overdue cards that punishes exactly the person who missed days. The verified
learning-science input for the increment found schedule shape to be
low-stakes (equal and expanding schedules perform the same; what matters is
long absolute gaps), which removes the main argument for a due-date queue.

## Decision

- Each card carries an Ebisu v2 Bayesian memory model (α, β, half-life,
  anchored at the last review) instead of a due date. The math is vendored
  into `core.study` from the public-domain reference implementation and
  pinned by unit tests against analytic values.
- Every consumer — chat quiz, brief, page — asks "which N cards are most at
  risk right now" via predicted recall. A lapse of any length changes
  nothing structurally; the next consumer simply reads today's ranking. No
  worker sweep exists because there is nothing to sweep.
- Chat answers map to Ebisu's native success value (AGAIN=0, HARD=0.6,
  GOOD=0.9, EASY=1.0; binary update at ≥0.5), avoiding the forced four-grade
  mapping conversational SRS has with SM-2/FSRS.
- Insurance: every review appends an immutable log row with rating, elapsed
  time, and the model triple before/after, so a later switch to FSRS can
  retrain from history.
- Semantic interference control in V1 is a soft cap (~10 new cards/day) in
  the tool description; embedding-based dispersion is deferred.

## Consequences

Review pressure self-regulates: the at-risk list is always the current
truth, and "ôn từ đi" works identically after one day or three weeks away.
The cost is that no card is ever "due", so habit cues must come from the
brief rather than the scheduler, and per-card intervals are not comparable
with Anki exports. The append-only review log keeps the algorithm decision
reversible.
