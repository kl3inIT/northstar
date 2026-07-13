# 0031 - Vocabulary Review Uses FSRS Due Scheduling

## Status

Accepted.

## Context

Decision 0015 chose Ebisu recall ranking for pull-based chat review and no
dedicated flashcard workflow. Decision 0024 later added a focused reviewer but
explicitly retained Ebisu. After recognition/production cards and
Again/Hard/Good/Easy controls landed, that split became misleading: the UI
looked Anki-like while HARD, GOOD, and EASY all produced the same binary-success
backend update. There were no learning/relearning states, due dates, interval
previews, lapses, persistent sibling burying, or leech detection.

The product now has a stable, deck-scoped review surface, so transparent
scheduling behavior matters more than the original no-backlog premise.

## Decision

- Vocabulary scheduling uses FSRS-6 through the official
  `io.github.open-spaced-repetition:fsrs:1.0.0` Java library. Core persists only
  provider-neutral state and maps library types inside `VocabScheduler`.
- Each vocabulary item has an independent recognition scheduling row and,
  when enabled, a production scheduling row. Content remains shared.
- Defaults are desired retention 90%, learning steps 1m/10m, relearning 10m,
  maximum interval 36500 days, default 21 parameters, and deterministic-seeded
  fuzzing so displayed and persisted outcomes agree.
- Ordinary sessions contain due, unburied cards only: intraday learning and
  relearning first, then review, then new. Retrievability breaks ties.
- Again from Review is a lapse and enters Relearning. Hard means successful but
  difficult recall; Again/Hard/Good/Easy each owns its real FSRS transition.
- Reviewing one direction buries its enabled sibling until the next local day.
  Eight review lapses mark that direction as a leech without auto-suspending the
  shared item.
- Review logs store before/after FSRS state for future optimization. Default
  parameters remain until enough single-user history justifies a separate,
  measured optimizer increment.
- The existing single-user scheduler state and old Ebisu review logs reset in
  V45; vocabulary content, decks, metadata, enrichment, and direction settings
  remain.

This decision supersedes Decision 0015's Ebisu model, binary rating mapping,
and no-due-date queue, and Decision 0024's preservation of those behaviors. It
does not change learner-owned rating, AI feedback advisory status, focused page
ownership, or explicit enrichment.

## Consequences

The reviewer now makes every rating's consequence visible and behaves like a
modern Anki FSRS workflow. Missed days can produce due cards, but session size
remains learner-selected and no escalating quota is manufactured. The system
gains more scheduling state and timezone-aware sibling handling, while keeping
the algorithm dependency behind one test-pinned boundary.
