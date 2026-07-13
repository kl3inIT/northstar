# Study Spec

## Current Behavior

Study combines a capture-first practice log, a focused vocabulary memory,
an LLM writing tutor, and measured speech practice with one
shared grammar-error history. There is no streak system,
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

Anki's mechanics rebuilt AI-native: each review direction carries an FSRS-6
memory state (Learning, Review, or Relearning; step, stability, difficulty,
due time, and last review). Northstar uses the official Open Spaced Repetition
Java implementation behind a provider-neutral core wrapper, desired retention
90%, learning steps 1m/10m, relearning 10m, maximum interval 36500 days, the
published 21 default parameters, and deterministic-seeded fuzzing. ~10 new
cards a day remains guidance in the tool description, not a hard wall.

Recognition (target expression → saved meaning) is always enabled. A card may
also enable production (saved, sense-specific meaning → target expression),
which owns an independent FSRS scheduling row and review-log direction without copying
the card content. Deck settings provide the default for newly-created cards;
existing cards stay recognition-only unless explicitly changed. Each queue
session selects at most one due direction for an item. Reviewing it buries the
enabled sibling until the next midnight in the browser's timezone.

- `front` is the word and `back` its meaning. Tone-marked pinyin/IPA
  `reading` and `partOfSpeech` are the base language fields. They travel in a
  `metadata` JSON string — never as columns — and remain empty when the model
  is unsure. Existing cards without either field remain valid.
- Every card belongs to exactly one `ENGLISH` or `CHINESE` library and may
  have one flat deck inside it. A missing deck is displayed as `General`;
  `All decks` is a review scope, not stored data. Changing language or deck
  never changes either FSRS schedule or immutable review history. Existing
  cards are backfilled deterministically (Han front → Chinese, otherwise
  English), without an AI call.
- Examples, collocations, synonyms, antonyms, easily-confused-word contrast,
  mnemonics, defensible word formation, a text-free mnemonic illustration, and
  provider-routed word/example audio
  are optional enrichment. Creating, loading, or revealing a card never
  generates them. The learner selects fields and explicitly starts an expiring
  in-API background preview, then keeps reviewing until a toast offers Apply or
  Discard. The review header retains a running/ready action until that decision,
  so preview access does not depend on a transient notification. Enrichment
  requests canonical lower-camel-case structured fields. Compatible gateways
  that discard the provider schema may return uppercase or snake-case aliases;
  the boundary normalizes those aliases, then applies the same requested-field
  completeness validation and one corrective retry. It never treats genuinely
  missing content as a successful enrichment. Word formation is omitted when
  decomposition is uncertain or malformed. Image bytes remain transient until
  Apply stores them through Attachments and references them as `frontImageId`;
  unknown metadata keys and existing user-authored values are preserved. Audio previews are likewise
  transient until Apply stores them in the content-addressed speech cache.
  Applied audio is bound to its exact source text; editing the front or example
  makes the binding stale and Listen falls back to browser speech rather than
  playing the wrong recording.
- Re-capturing an existing front (accent- and case-insensitive) returns the
  existing card instead of duplicating it.
- Focused reviews happen on the Vocabulary page; chat may still conduct a
  quiz. The learner first selects English or Chinese, then `All decks`,
  `General`, or one saved deck such as IELTS/HSK4. A browser-owned 10/20/30-card
  session takes due, unburied items only inside that scope and never mixes
  languages. Intraday Learning/Relearning comes first, then Review, then new;
  lower retrievability breaks ties. Production
  prompts hide listening until reveal. Optional free-text answer checking
  returns `CORRECT`, `CLOSE`, or `MISSED` with a short explanation but does
  not select a rating or update memory. Only the learner's subsequent
  Again/Hard/Good/Easy action records a `MANUAL` review. The four buttons show
  server-computed next intervals and own distinct FSRS outcomes. Again from
  Review is a lapse and enters Relearning; Hard means the learner did recall
  with difficulty and must never stand in for a forgotten answer.
- Every review appends to an immutable log carrying rating, elapsed days, and
  state/step/stability/difficulty/due before and after. At eight Review-state
  lapses the direction is visibly marked as a leech but is not silently hidden.
- Cards can be suspended (kept with history, excluded from at-risk) and
  deleted (cascades the review log).
- Listen uses valid applied speech first and `window.speechSynthesis` as the
  free default/fallback. Production prompts cannot play the target before
  reveal. Practice has three modes: Word assesses the current front;
  Shadowing plays and assesses the current saved example (or front fallback);
  Dictation hides the same reference and uses a deterministic case- and
  punctuation-tolerant word diff. Word and Shadowing recordings are playable
  before submit and persist only after a successful assessment as 16-kHz,
  16-bit mono PCM WAV. Attempt facts remain; recording bytes expire after 180
  days and are capped at 20 per card/mode while pinned, first, best, and latest
  recordings are preserved. History is newest-first with pin/delete and
  provider-aware Accuracy/Fluency/Prosody trends. These provider-native 0-100
  values are never converted to IELTS, and none of the three modes changes
  either FSRS schedule.

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
- The production API image includes the Speech SDK's native Linux runtime
  dependencies, including `libstdc++.so.6`, `libuuid.so.1`, OpenSSL, ALSA, and
  CA certificates. Image construction fails if either native runtime is absent
  so pronunciation does not degrade into a first-request `500` after an
  otherwise healthy boot.
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
  supplies language plus reading and part of speech when known, preserves an
  explicitly named deck, but invents neither a deck nor optional enrichment).
  An intention to study later stays a task.
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
  Vocabulary: separate English/Chinese tabs, a deck selector, and scoped
  tracked/at-risk/new-this-week stats plus search across word, meaning,
  reading, and part of speech. Each table row shows its deck and the table is
  sorted due-first then by retrievability, with suspended cards last. It provides
  pause/resume/edit/delete, language/deck correction, live
  Word/Shadowing/Dictation audio practice with persistent attempt history, and
  a keyboard-first weak-card reviewer. The
  reviewer supports recognition and optional production, typed or dictated
  answers, click-anywhere-on-the-non-interactive-card or Space flipping,
  explicit semantic checking, and a compact answer face. Answer, reading, part
  of speech, and one example remain visible; richer usage/relationship/memory
  fields live under a counted `Study details` disclosure. Expanded details
  scroll inside the card while the rating footer remains visible,
  Space to flip between question and answer, 1-4 to rate with the real next
  interval, R to listen, Escape to exit, a completion
  tally, deck/per-card two-direction controls, applied-audio-first Listen with
  browser fallback, and an opt-in background
  enrichment Sheet whose ready preview (including audio players) replaces the field picker instead of
  forcing a scroll. Applied mnemonic images appear only on the question/front
  face; word-formation parts appear on the answer face. Writing:
  essays graded, latest estimate,
  trend vs previous essay, recurring-error strip, feedback table with a
  detail dialog (per-criterion justifications, quote-to-fix errors, the
  essay) and delete. Speaking: attempt count and recent delivery trend,
  question generation, 60-second recorder, separately-labelled delivery,
  content, and unofficial IELTS-style ranges, grounded expandable evidence,
  highlighted transcript errors, history detail, and delete.
- Vocab review and speech assessment have focused page workflows; chat remains
  an optional entry path for vocab quizzes. Writing grading remains in chat.
- REST surface: `/api/study/sessions` (GET/POST/PUT/DELETE), `/summary`,
  `/mocks`, `/skills`, `/vocab` (GET/POST/PUT/DELETE),
  `/vocab/review` (required language, optional deck, due direction-specific rows
  with rating previews),
  `/vocab/decks/settings`, `/vocab/{id}/reviews`,
  `/vocab/{id}/answer-check`,
  `/vocab/{id}/enrichment`, `/vocab/{id}/enrichment-jobs` plus job
  status/apply/discard, `/vocab/{id}/pronunciation`,
  `/vocab/{id}/dictation-attempts`, `/vocab/{id}/audio-attempts`, and
  recording serve/pin/delete under `/vocab/audio-attempts/{attemptId}`, `/writing`
  (GET/DELETE), and `/speaking`
  history plus `/speaking/question` and `/speaking/attempts`. Week boundaries
  respect the `X-Timezone` header.

## Review Integration

The weekly review's facts include a study section when there was activity:
sessions and minutes by skill, mock results, and words slipping below 70%
recall. The morning-brief section waits for the automation/brief increment
to land.
