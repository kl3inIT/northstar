# Study Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Ebisu memory model math | Automated | `core/src/test/java/com/northstar/core/study/EbisuTests.java` pins the vendored port to analytic values: expected recall at the conjugate point, moment-matched updates, half-life growth/shrink, balanced-model half-life identity, monotonic decay, gammaln reference values, and 30-iteration lapse stability. |
| Writing grade structural checks | Automated | `core/src/test/java/com/northstar/core/study/WritingGraderTests.java` pins the code half of the evaluator loop: half-band steps, range width and ordering, verbatim quote check with whitespace tolerance, missing criteria, empty summary. |
| Skill canonicalization and score-pair validation | Gap | Accent-insensitive skill merge and both-or-neither score validation are exercised only through integration flows; a focused `StudyService` unit test is missing. |
| Vocab create dedupe, at-risk ranking, review logging | Gap | `VocabService` paths (existing-front return, predictRecall ordering, MIN_ELAPSED clamp, before/after log rows) need a Testcontainers integration test. |
| Study/vocab/writing REST round trip | Gap | No `StudyControllerIntegrationTests` yet; schema V29-V31 is only covered by Flyway migration on boot. |
| Capture STUDY and VOCAB classification | Runtime verified | Live-model E2E on 2026-07-11: one Vietnamese sentence became two sessions with split scores and discipline links; a vocab sentence produced two enriched cards (tone-marked pinyin, IPA, generated examples). Prompt-content assertions in `CaptureServiceIntegrationTests` do not yet cover the study/vocab sections. |
| grade_writing end-to-end with evaluator loop | Runtime verified | Live-model E2E on 2026-07-11 (three gradings of a planted-error essay): stable 5.0-5.5 range across grader v1/v2, verbatim-quoted justifications, under-length called out, prior-error persistence named in the summary. |
| Assistant tool routing for "chấm bài" | Runtime verified | Before the system-prompt route, the model graded by hand from KB notes and saved nothing; after, a fresh conversation called `grade_writing`. Re-verify when the assistant system prompt changes. |
| OpenAPI required fields and generated web types | Static | Regenerate `contracts/openapi.json`, run `pnpm -C web gen:api`, then `pnpm -C web typecheck`. |
| /study page and Capture study controls | Runtime verified | Playwright walkthroughs on 2026-07-11 at 1440x900: Log tab (stat strip, mock-trend chart with per-skill lines and 0-100% axis, filters, table), Vocabulary tab (stats, search, at-risk sort, pause), Writing tab (stats, recurring strip, detail dialog), Capture chips and unified Recent rows; browser console clean. |
| Live model grading agreement | Gap | No calibration set of officially-scored essays is wired into an opt-in evaluation; agreement numbers come from the literature (see decision 0016), not from this codebase. |
