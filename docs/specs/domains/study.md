# Study Spec

## Current Behavior

Study combines a capture-first practice log, a vocabulary memory reviewed
through chat, an LLM writing tutor, and measured speech practice with one
shared grammar-error history. There is no due-date queue, streak system,
lesson content, or conversational voice agent: entries arrive through
Capture/chat or the focused speech workflows, and `/study` answers "how much,
what, and is it moving".

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

Asking the Assistant for the meaning, translation, or explanation of one
specific foreign-language word or phrase is treated as intent to learn it. The
Assistant answers and saves an enriched card in the same turn unless the user
explicitly says not to save. A contextual reference such as "that word" must
resolve to exactly one lexical item; incidental words in prose, quotations, or
research are not auto-saved.

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
- A card can be read aloud from its row. The browser records raw mic samples,
  downsamples to 16-kHz mono PCM, wraps a WAV in memory, and sends it with the
  card id. The server uses the card front as the immutable reference text and
  chooses `zh-CN` when it contains a Han ideograph, otherwise `en-US`.
  Provider-measured 0-100 accuracy/fluency/prosody and word/phoneme detail are
  shown live and are not persisted as card history. Audio is never persisted.

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
- The grader resolves the `STUDY_GRADER` AI task route at call time and stores
  the selected model id on every row.
- Feedback rows are append-only: delete, never edit. Essays are capped at
  18,000 characters and must have at least 30 words.

### Speaking practice

`/study` provides one-question speaking practice, deliberately not a realtime
exam simulation. The learner selects Part 1/2/3, the `STUDY_GRADER` route
generates one concise examiner-style question, and the browser records at most
60 seconds as a 16-kHz mono PCM WAV. The server accepts at most ~2.5 MB and
rejects WAVs beyond its 75-second safety ceiling. Audio is decoded in memory
and never persisted.

- `SpeechAssessor` is a provider-neutral `core.study` port. The current
  `integrations/speech-azure` adapter uses Microsoft Speech SDK 1.50.0 through
  an in-memory push stream; only `apps/api` wires it. Core never imports Azure
  types. Blank configuration leaves the API bootable and speech endpoints
  return 503.
- Azure is authoritative only for delivery: transcript, pronunciation,
  fluency, prosody, and word/phoneme accuracy. These remain provider 0-100
  values, are never relabelled or directly converted into an IELTS band, and
  stay visibly separate from rubric-based estimates.
- The selected `STUDY_GRADER` AI route scores vocabulary, grammar, and topic on
  a separate unofficial 0-100 practice scale. It receives the prior writing
  and speaking error corpus, must quote transcript fragments verbatim, and
  passes structural plus LLM faithfulness evaluation with one corrective
  retry. The evidence envelope contains the question, transcript, and measured
  delivery; the LLM cannot alter provider scores.
- The same structured grading call assesses four IELTS-style criteria against
  `prompts/rubrics/ielts-speaking.md`: Fluency and Coherence, Lexical Resource,
  Grammatical Range and Accuracy, and Pronunciation. Each criterion is a
  half-band range no wider than 1.0 with LOW or MEDIUM confidence and grounded
  evidence. FC/LR/GRA evidence must quote the transcript; Pronunciation may
  use only measured delivery values and low-accuracy words. Java validates the
  evidence and deterministically averages the four bounds into an overall
  range. It is always labelled `Unofficial one-answer IELTS-style estimate`
  with LOW overall confidence; it is not an official score or a full Part 1-3
  assessment.
- A successful attempt persists question, transcript, both score groups,
  exact writing-compatible `topErrors` JSON (`label`, `quote`, `fix`), summary,
  grader model, delivery provider and provider revision, the complete estimate
  snapshot, and `ielts-speaking-one-answer-v1` scorer version. Legacy rows have
  no estimate and remain readable. It also logs one `Speaking` / `PRACTICE`
  session rounded up to whole minutes.
- History is newest-first with detail and delete. Provider identity is stored
  because different vendors' 0-100 scales are not assumed comparable.

### Grammar drills

`grammar_weaknesses` aggregates error patterns across writing and speaking
feedback — grouped case-insensitively by label, most recently seen first,
each with up to three recent quote→fix examples from the user's own text. Its tool
description carries the drill protocol: the assistant picks the 1-2 most
recent patterns (focused practice), checks recent Grammar sessions to avoid
re-drilling, writes five NEW single-error sentences at the user's level,
presents them one at a time, and after each answer gives the verdict, the
corrected sentence, and a one-line rule explanation. The finished (or
stopped) drill is logged as a study session (skill Grammar or Vocabulary,
score = correct/items, notes naming the patterns). With no writing or speaking
feedback the tool returns an empty list and the assistant offers a supported
practice path instead of inventing weaknesses. Malformed stored error JSON is
skipped, never fatal.

## Entry Paths

- Capture classifies `STUDY` (practice already done — multi-item, echo-back,
  undo, same contract as expenses) and `VOCAB` (words to memorize; the model
  enriches with reading and an example sentence only when confident). An
  intention to study later stays a task.
- Assistant and MCP tools: `log_study_sessions`, `find_study_sessions`,
  `update_study_session`, `delete_study_session`, `study_summary`,
  `list_mock_results`, `save_vocab_cards`, `find_vocab_cards`, `quiz_vocab`,
  `record_vocab_review`, `update_vocab_card`, `delete_vocab_card`,
  `list_writing_feedback`, `delete_writing_feedback`,
  `list_speaking_feedback`, `delete_speaking_feedback`,
  `grammar_weaknesses`.
- `grade_writing` is in-app only (no MCP annotation): grading needs the LLM
  the mcp app deliberately does not have. The assistant system prompt routes
  "chấm bài" to it — tool discovery is search-based, and without the route
  the model graded essays by hand from knowledge-base notes.

## Read And Correction Paths

- `/study` has four tabs. Log: weekly stat strip, mock-trend line chart
  (score percentage per mock, one line per skill, hidden until two scored
  mocks exist), skill/kind filters, sessions table with edit/delete.
  Vocabulary: tracked/at-risk/new-this-week stats, search across word,
  meaning, and reading, table sorted most-at-risk first with suspended cards
  last, pause/resume/edit/delete plus live pronunciation assessment. Writing: essays graded, latest estimate,
  trend vs previous essay, recurring-error strip, feedback table with a
  detail dialog (per-criterion justifications, quote-to-fix errors, the
  essay) and delete. Speaking: attempt count and recent delivery trend,
  question generation, 60-second recorder, separately-labelled delivery,
  content, and unofficial IELTS-style ranges, grounded expandable evidence,
  highlighted transcript errors, history detail, and delete.
- Vocab reviews and writing grading remain in chat; speech assessment runs on
  the focused page because chat has no audio attachment/output workflow.
- REST surface: `/api/study/sessions` (GET/POST/PUT/DELETE), `/summary`,
  `/mocks`, `/skills`, `/vocab` (GET/POST/PUT/DELETE),
  `/vocab/{id}/pronunciation`, `/writing` (GET/DELETE), and `/speaking`
  history plus `/speaking/question` and `/speaking/attempts`. Week boundaries
  respect the `X-Timezone` header.

## Review Integration

The weekly review's facts include a study section when there was activity:
sessions and minutes by skill, mock results, and words slipping below 70%
recall. The morning-brief section waits for the automation/brief increment
to land.
