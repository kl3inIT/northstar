# Study Tutor V1 — Design

Research input: [research.md](./research.md). Philosophy constraints carried
in: capture-first, no daily ritual (AI-drafted, reading-first), review happens
in chat and the morning brief, no streaks/gamification, no flashcard app.

## Scope

In V1:

1. **Study log** — sessions captured in natural language, structured by AI.
2. **Vocabulary memory** — cards captured in natural language, reviewed
   through chat quizzes and the morning brief (recall-probability model).
3. **Writing tutor** — LLM grading of essays against per-criterion rubrics,
   with a persistent error history.
4. **Mock-test tracking** — mock results are study sessions with scores;
   progress reads against the exam-dated project.

Not in V1 (deliberate): speaking assessment (no reliability evidence), any
flashcard UI, streaks/leaderboards, lesson content, LanguageTool sidecar
(V1.5 hardening), bulk vocab imports and sentence-corpus enrichment (V1.5),
embedding-based semantic dispersion of new cards (V1.5 — V1 uses a simple
new-cards/day cap).

## Decision 1 — SRS model: recall probability (Ebisu), not a due-date queue

The verified science says schedule shape is low-stakes (equal ≡ expanding;
what matters is long absolute gaps), while the canonical SRS failure mode is
the post-lapse backlog. A due-date queue (SM-2/FSRS) manufactures that
backlog; a recall-probability model (Ebisu: Bayesian posterior over each
card's half-life) has **no due dates at all** — every consumer just asks
"which N cards are most at risk right now" (`predictRecall`) whenever it
runs. That is exactly the pull-based shape of the brief and chat:

- Morning brief: top-N at-risk words, each as a retrieval question.
- Chat: "ôn từ đi" → quiz over the current at-risk ranking.
- Missed days change nothing structurally — probabilities decay, the next
  brief simply picks today's top-N. No debt, no catch-up sweep, no worker job.

Mechanics: port the Ebisu math into `core.study` (public-domain Unlicense;
official ebisu-java exists as reference). Card state = the (α, β, halflife)
triple + last-review timestamp. Chat answers map to Ebisu's native
success/failure (with fuzzy partial credit), avoiding the forced 4-grade
mapping problem conversational SRS has with SM-2/FSRS.

Insurance: every review appends to `vocab_review_log` carrying
FSRS-compatible fields (rating when inferable, elapsed time, state
before/after) so a later switch to FSRS can retrain from history.

Semantic interference: V1 caps new-card introductions per day (default 10)
via tool-description guidance; embedding-based "don't introduce near-synonyms
together" (pgvector is already in the stack) is V1.5.

## Decision 2 — Entities (`core.study`, migration V28+)

- `study_session` — occurredOn, skill (constrained vocabulary: seed
  `listening/reading/writing/speaking/vocabulary/grammar/other` ∪ used,
  accent-insensitive canonicalization reused from finance), disciplineId?,
  kind `PRACTICE|MOCK`, durationMinutes?, scoreRaw?/scoreMax?, notes?,
  source `CAPTURE|ASSISTANT|MANUAL`, version. A mock is a session with
  kind=MOCK and a score.
- `vocab_card` — front, back, disciplineId?, metadata jsonb (pinyin,
  traditional, audioUrl, examples… — language specifics live here, never as
  columns), α/β/halflifeHours, lastReviewedAt?, suspended, version.
- `vocab_review_log` — cardId, reviewedAt, success (0..1), rating?
  (`AGAIN|HARD|GOOD|EASY` when inferable), elapsedHours, source
  `BRIEF|CHAT`, modelBefore/modelAfter jsonb. Append-only.
- `writing_feedback` — submittedAt, taskLabel, essayMarkdown, bands jsonb
  (per criterion + overall **estimate range**), topErrors jsonb, graderModel
  (pinned model id), version.

Generic-first: nothing IELTS/HSK-specific in schema. Rubrics are prompt
resources (`prompts/rubrics/ielts-writing.md` first), selected per request —
a plug-in point, not a table, until a second rubric exists.

## Decision 3 — Entry paths

- Capture classifier gains kinds `STUDY` and `VOCAB` (multi-item, echo-back,
  undo — same contract as finance). "làm listening HSK4 đúng 18/25" → session;
  "từ mới: 磨蹭 = lề mề" → card.
- Assistant/MCP tools (dual `@Tool`+`@McpTool`, descriptions per the audited
  checklist): `log_study_sessions`, `find_study_sessions`,
  `update_study_session`, `delete_study_session`, `study_summary`,
  `save_vocab_cards` (batch, new-card cap guidance), `list_vocab`
  (at-risk-ranked), `record_vocab_review`, `grade_writing`,
  `list_writing_feedback`, `delete_vocab_card`.
- No REST-only surface beyond what the web page reads.

## Decision 4 — Tutor grading contract

Per the verified evidence: rubric prompt = official band descriptors per
criterion + calibrated exemplar essays per band + **required per-criterion
justification**; output is an estimate range ("~6.0–6.5"), explicitly framed
as unofficial; grader model id is pinned and stored on the feedback row.
Two-pass shape (LLM-AES): the tool returns the fast per-criterion estimate +
top-3 recurring errors inline; deeper analysis happens conversationally.
Each grading appends structured errors so the next grading compares against
the corpus ("lỗi article lần trước còn nguyên").

## Decision 5 — Brief, review, page

- Morning brief study section: yesterday's study in one line, top-N at-risk
  words as retrieval questions (answers hidden behind the chat), one
  "hôm nay ôn gì" suggestion derived from exam distance + weakest skill.
  Retrieval questions, never summaries (Dunlosky).
- Weekly review section: hours by skill vs last week, mock trend, vocab
  stats (reviewed/at-risk), descriptive tone — same contract as the money
  section.
- `/study` page (read-mostly, replaces the sidebar stub): stat strip
  (week hours, sessions, tracked words, at-risk count), sessions table,
  vocab table sorted by recall probability, writing-feedback history,
  mock-score trend. UI vocabulary: existing shadcn + Kibo + the
  data-table/URL-filter patterns from research.

## Module wiring

`core.study` (new Modulith module) exposed via root public types;
`capture→study`, `assistant→study`, `alignment→study` dependencies; **no
worker job needed** (pull-based model has nothing to sweep). Web page under
`web/src/features/study/`.
