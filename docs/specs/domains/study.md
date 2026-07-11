# Study Spec

## Current Behavior

Study combines a capture-first practice log, a vocabulary memory reviewed
through chat, and an LLM writing tutor with a persistent error history. There
is no flashcard UI, no due-date queue, no streaks, and no lesson content:
entries arrive through Capture or chat, and `/study` is a read-mostly view
that answers "how much, what, and is it moving".

### Study sessions

Each session stores:

- the date it happened;
- a skill from a constrained vocabulary (seed: Listening, Reading, Writing,
  Speaking, Vocabulary, Grammar, Other — unioned with skills already used,
  with accent-insensitive canonicalization reused from finance);
- kind `PRACTICE` or `MOCK` (a mock is a full practice test);
- optional whole-minute duration, optional score pair (`scoreRaw`/`scoreMax`,
  both or neither), optional notes, optional discipline link;
- its entry source: `CAPTURE`, `ASSISTANT`, or `MANUAL`.

The weekly summary reports total minutes, session count, per-skill minutes
largest-first, and the previous ISO week for comparison. It is descriptive,
never a quota. Mock results are read oldest-first as the progress trend.

### Vocabulary memory

Anki's mechanics rebuilt AI-native: each card carries an Ebisu v2 memory
model (`alpha`, `beta`, `halflifeHours`, anchored at `lastReviewedAt`) instead
of a due date. Every consumer asks "which N cards are most at risk right now"
via predicted recall, so missed days never build a backlog. New cards start
balanced (α=β=2) at a one-day half-life; ~10 new cards a day is guidance in
the tool description, not a hard wall.

- `front` is the word, `back` the meaning. Language specifics (tone-marked
  pinyin/IPA reading, an AI-generated example sentence with translation)
  travel in a `metadata` JSON string — never as columns.
- Re-capturing an existing front (accent- and case-insensitive) returns the
  existing card instead of duplicating it.
- Reviews happen in chat: the assistant quizzes at-risk cards one at a time
  (never revealing the back first), grades free-text answers by meaning
  equivalence, and records each review. Ratings map to Ebisu success values:
  AGAIN=0, HARD=0.6, GOOD=0.9, EASY=1.0 (binary update at ≥0.5).
- Every review appends to an immutable log carrying rating, elapsed hours,
  and the model triple before/after — enough for a future FSRS-style
  optimizer to retrain from history.
- Cards can be suspended (kept with history, excluded from at-risk) and
  deleted (cascades the review log).

### Writing tutor

`grade_writing` grades one essay per call against a rubric prompt resource
(`prompts/rubrics/ielts-writing.md`) and appends to `writing_feedback`:

- The rubric embeds the official public IELTS band descriptors verbatim
  (bands 1-9 for TR/TA, CC, LR, GRA) plus five officially-scored anchor
  essays (bands 4-8) and calibration rules. Grading is comparative: anchor
  placement decides the holistic overall estimate; per-criterion bands are
  diagnostic and are not averaged into it.
- The result is an UNOFFICIAL estimate range (`overallMin`..`overallMax`,
  half-band steps, at most 1.0 wide), per-criterion bands with justifications
  that must quote the essay, and 1-3 recurring error patterns each carrying a
  verbatim quote and its fix.
- The last ten gradings' error patterns are injected into every new grading,
  so the summary names patterns that persist across essays (and ones that
  were fixed).
- Every grading passes an evaluator-optimizer loop: structural checks in
  code (band steps, range width, verbatim quotes) then an LLM faithfulness
  evaluator (a Spring AI `Evaluator` implementation); failures feed one
  corrective re-grade, then the call fails loudly.
- The grader model id is pinned by configuration
  (`northstar.study.grader-model`) and stored on every row.
- Feedback rows are append-only: delete, never edit. Essays are capped at
  18,000 characters and must have at least 30 words.

## Entry Paths

- Capture classifies `STUDY` (practice already done — multi-item, echo-back,
  undo, same contract as expenses) and `VOCAB` (words to memorize; the model
  enriches with reading and an example sentence only when confident). An
  intention to study later stays a task.
- Assistant and MCP tools: `log_study_sessions`, `find_study_sessions`,
  `update_study_session`, `delete_study_session`, `study_summary`,
  `list_mock_results`, `save_vocab_cards`, `find_vocab_cards`, `quiz_vocab`,
  `record_vocab_review`, `update_vocab_card`, `delete_vocab_card`,
  `list_writing_feedback`, `delete_writing_feedback`.
- `grade_writing` is in-app only (no MCP annotation): grading needs the LLM
  the mcp app deliberately does not have. The assistant system prompt routes
  "chấm bài" to it — tool discovery is search-based, and without the route
  the model graded essays by hand from knowledge-base notes.

## Read And Correction Paths

- `/study` has three tabs. Log: weekly stat strip, mock-trend line chart
  (score percentage per mock, one line per skill, hidden until two scored
  mocks exist), skill/kind filters, sessions table with edit/delete. 
  Vocabulary: tracked/at-risk/new-this-week stats, search across word,
  meaning, and reading, table sorted most-at-risk first with suspended cards
  last, pause/resume/edit/delete. Writing: essays graded, latest estimate,
  trend vs previous essay, recurring-error strip, feedback table with a
  detail dialog (per-criterion justifications, quote-to-fix errors, the
  essay) and delete.
- Reviews and grading never happen on the page — both tabs point at chat.
- REST surface: `/api/study/sessions` (GET/POST/PUT/DELETE), `/summary`,
  `/mocks`, `/skills`, `/vocab` (GET/POST/PUT/DELETE), `/writing`
  (GET/DELETE). Week boundaries respect the `X-Timezone` header.

## Review Integration

The weekly review's facts include a study section when there was activity:
sessions and minutes by skill, mock results, and words slipping below 70%
recall. The morning-brief section waits for the automation/brief increment
to land.
