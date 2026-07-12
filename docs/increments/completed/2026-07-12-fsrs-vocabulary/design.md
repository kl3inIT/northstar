# FSRS Vocabulary Scheduling — Design

## Goal

Replace the vocabulary reviewer's Ebisu risk ranking with a transparent,
Anki-compatible FSRS workflow: due queues, independent recognition and
production schedules, meaningful Again/Hard/Good/Easy outcomes, learning and
relearning steps, sibling burying, and leech detection.

This increment supersedes the scheduling parts of Decisions 0015 and 0024.
It does not change card content, opt-in AI enrichment, pronunciation, deck
partitioning, or the rule that only the learner's rating updates memory.

## Decisions

### D1 — Use the maintained Java FSRS-6 implementation

Core depends on `io.github.open-spaced-repetition:fsrs:1.0.0`, the official
Open Spaced Repetition Java implementation. Northstar maps its provider-neutral
persistence model to the library's `Card`, `State`, and `Rating` at the service
boundary; library JSON and library types never appear in REST contracts or the
database.

FSRS-6 is the stable production target supported by that artifact. FSRS-7 is
still an evolving research/benchmark target and is not hand-ported. The
scheduler uses the published 21 default parameters until Northstar has enough
real review history for an explicit optimizer increment.

### D2 — Scheduling is a child of the vocabulary item and review direction

`vocab_card` remains the shared content note. `vocab_scheduling_card` owns one
FSRS state per `(vocab_card_id, direction)`:

- state and learning step;
- stability and difficulty;
- due and last-review instants;
- lapse count, leech marker, and sibling-bury boundary;
- optimistic version.

Recognition always has a scheduling row. Production retains its existing
per-item enable flag and gets an independent row when first enabled. Disabling
production hides that direction without deleting learned state. Changing
content, language, or deck does not rewrite either schedule.

### D3 — Reset the single-user scheduling state cleanly

Migration V45 creates the FSRS table, creates fresh recognition rows for every
item and production rows for enabled items, replaces the Ebisu-shaped review
log with an FSRS-shaped append-only log, and removes obsolete Ebisu columns.
Existing vocabulary content, decks, metadata, enrichment, and production
preferences remain intact; prior scheduler state and review-log rows are
discarded as explicitly acceptable for this single-user deployment.

Applied migrations are never edited. `Ebisu.java` and its tests are removed
only after the new scheduler tests are green.

### D4 — Due items form the review queue

Only active, enabled scheduling rows whose `due_at <= now` and
`buried_until <= now` enter ordinary review. Within the selected language and
deck, intraday Learning/Relearning cards come first, then Review cards, then
new Learning cards. Ties use ascending retrievability and due time. The caller's
10/20/30 limit remains a session-size cap; Northstar does not manufacture a
larger catch-up quota after missed days.

Library and chat summaries expose current retrievability, state, due time,
stability, lapse/leech status, and review count. A card is "at risk" when its
FSRS retrievability is below the existing 70% reporting threshold, independent
of whether it is currently due.

### D5 — Ratings own four real, previewable outcomes

The scheduler uses desired retention `0.90`, learning steps `1m, 10m`,
relearning step `10m`, maximum interval `36500` days, and fuzzing enabled.
Before reveal/rating, each review row includes server-computed previews for
Again, Hard, Good, and Easy: next state, next due instant, and a compact
interval label.

Fuzzing remains enabled without preview drift by rebuilding the scheduler with
a deterministic seed derived from scheduling-card id, optimistic version, and
the preview timestamp. The POST carries that preview timestamp and expected
schedule version; the server recomputes the selected outcome, rejects stale
input, and persists exactly the previewed result. Clients never submit memory
state or due dates.

### D6 — Lapses and ordinary reviews are distinct

Again on a Review card is a lapse: the card enters Relearning, its lapse count
increments, and the `10m` relearning step applies. Again during initial
Learning is not a lapse. Hard/Good/Easy are successful recalls with distinct
FSRS transitions; Hard must never be used for a forgotten answer.

Each log row stores state, step, stability, difficulty, due, and last-review
before/after, plus rating, source, direction, review time, and elapsed days.
This preserves an optimizer-ready history without coupling the domain to an
optimizer runtime.

### D7 — Siblings are buried until the next local day

After a recognition or production review, the enabled sibling direction is
buried until the next midnight in the browser's `X-Timezone`. This persists
across page refreshes and new review sessions, then expires automatically. It
does not change the sibling's FSRS due date or memory state.

### D8 — Leech detection warns without silently hiding material

At eight Review-state lapses a scheduling direction becomes a leech. The flag
is visible in the library and review UI, while the item remains reviewable.
Northstar does not auto-suspend the whole vocabulary item because one weak
direction must not hide its healthy sibling. Editing or explicitly pausing the
item remains learner-owned. A later workflow may add per-direction remediation
and reset controls.

## Verification

1. Migration V45 validates on a clean database and preserves vocabulary content.
2. Unit tests pin all four rating outcomes, learning/relearning transitions,
   deterministic previews, due filtering/order, timezone sibling burying,
   independent directions, lapse counting, and the leech threshold.
3. API tests pin timezone, version/preview concurrency, and learner-owned ratings.
4. Web tests pin due-only session entry, interval labels, keyboard ratings,
   leech state, and persistent review access.
5. OpenAPI generation, Java gates, web tests/typecheck/build, and Spring Modulith
   verification are green.
6. A local browser pass reviews a new card through Learning and exercises a
   Review lapse/relearning path once before consolidation.

## Completion evidence

- The official FSRS-6 Java artifact and exact builder/card APIs were verified
  from Maven binary and source JARs before implementation.
- V1 through V45 migrated on an isolated PostgreSQL database and Hibernate
  schema validation completed successfully.
- Java compile/test plus Spring Modulith/core verification, web lint/tests,
  TypeScript typecheck, and production build are green. Lint reports only the
  existing repository warnings outside this increment.
- A real 1440x1000 Chromium pass reviewed two fresh IELTS cards. The UI showed
  Learning previews `1m / 6m / 10m / 12-13d`; Good persisted Learning step 1,
  Easy persisted Review, and the production sibling was buried to the next
  Asia/Bangkok midnight. After making that Review card due, the UI showed
  `10m / 10d / 20d / 1mo`; Again persisted Relearning step 0, a 10-minute due
  time, and lapse count 1. All three writes returned 200 and the console was clean.
- Decision 0030, Study spec/test matrix, roadmap, and Northstar App Behavior
  are updated.
