# Study Tutor V1 — Plan

Execute in four blocks; each block ships usable and gated
(compileJava + compileTestJava + :core:test + web typecheck).

## Block 1 — Study log (foundation)

1. Migration `V28__study.sql`: `study_session` (occurred_on, skill, kind
   PRACTICE|MOCK, duration_minutes?, score_raw?/score_max?, notes?,
   discipline_id?, source, audit columns, version).
2. `core.study` module: `StudySession` entity, `StudyService`
   (record/list/update/delete/summary, skill vocabulary seed ∪ used with
   accent-insensitive canonicalization — finance pattern), summaries as
   records, Modulith-clean.
3. Capture: classifier kind `STUDY` (multi-item like expenses), draft →
   `StudyService.recordAll(source=CAPTURE)`, echo + undo contract.
4. Assistant tools `StudyTools` (dual @Tool/@McpTool, audited-checklist
   descriptions): `log_study_sessions`, `find_study_sessions`,
   `update_study_session`, `delete_study_session`, `study_summary`.
5. API: `GET/POST /api/study/sessions`, `PUT/DELETE /{id}`,
   `GET /api/study/summary?weeks=`, `GET /api/study/skills`; regen contract.
6. Web `/study` page (replaces sidebar stub): header + tabs
   (Log | Vocabulary | Writing — later tabs placeholdered), stat strip,
   sessions table with skill/kind filters, edit/delete dialogs.

## Block 2 — Vocabulary SRS

1. Migration `V29__vocab.sql`: `vocab_card` (front, back, metadata jsonb,
   alpha/beta/halflife_hours, last_reviewed_at?, suspended, version),
   `vocab_review_log` (append-only, FSRS-compatible fields).
2. Ebisu math ported into `core.study` (public domain; property-tested
   against reference values), `VocabService`: create cards (new/day cap
   guidance), `atRisk(n)` via predictRecall, `recordReview(success)`.
3. Capture kind `VOCAB`; assistant tools `save_vocab_cards`, `list_vocab`,
   `record_vocab_review`, `delete_vocab_card` + quiz flow guidance in the
   assistant system prompt (Kotoba-style session inside chat).
4. Vocabulary tab on `/study` (at-risk ranking, recall %, edit/suspend).
5. Alignment weekly review study section; brief section deferred until the
   in-flight automation/morning-brief increment lands (avoid conflicts).

## Block 3 — Writing tutor

1. Migration `V30__writing_feedback.sql`.
2. Rubric prompt resource (IELTS Task 2 descriptors + calibrated exemplars +
   per-criterion justification), grading service (pinned model id, estimate
   ranges, top-errors extraction, prior-corpus comparison).
3. Tools `grade_writing`, `list_writing_feedback`; Writing tab on `/study`.

## Block 4 — Mock trend + consolidation

1. Mock-score trend on the Log tab (kind=MOCK sessions).
2. Consolidate: `docs/specs/domains/study.md`, tests matrix, decision record,
   App Behavior note update, roadmap update, move increment to completed.

Out of scope guardrail: do not touch the uncommitted automation/brief
workstream files; study's brief integration waits for it to land.
