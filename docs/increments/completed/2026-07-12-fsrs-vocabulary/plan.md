# FSRS Vocabulary Scheduling — Plan

Status: completed 2026-07-12. All four blocks, local gates, clean-schema boot,
and browser/API/DB verification passed; Decision 0031 and Study sources of truth
contain the consolidated behavior.

## Block A — Persistence and scheduler boundary

- Add the verified Java FSRS dependency through the version catalog.
- Add V45 and the scheduling entity/repository.
- Replace Ebisu state and revlog fields with provider-neutral FSRS state.
- Add a deterministic `VocabScheduler` wrapper and focused algorithm tests.
- Gate: `./gradlew --no-daemon compileJava compileTestJava :core:test`.

## Block B — Domain queue and REST contracts

- Create/retain direction schedules with vocabulary lifecycle changes.
- Implement due ordering, retrievability, four outcome previews, optimistic
  rating writes, lapse/leech behavior, and timezone sibling burying.
- Update Assistant/MCP-facing summaries without exposing library types.
- Update controller requests and tests.
- Gate: Java compile/core/API tests and Spring Modulith verification.

## Block C — Focused reviewer UX

- Make the library's review action due-aware and persistent.
- Show schedule state, due counts, leech status, and real interval labels on
  Again/Hard/Good/Easy.
- Preserve reveal, keyboard, pronunciation, enrichment, deck, and direction UX.
- Regenerate OpenAPI/client and update web state tests.
- Gate: `pnpm -C web test`, `pnpm -C web typecheck`, `pnpm -C web build`.

## Block D — Runtime verification and consolidation

- Run clean-schema Flyway/Hibernate validation and all local Java/web gates.
- Exercise new-learning, successful review, sibling bury, and lapse/relearning
  in the real browser with a clean console.
- Add Decision 0031 superseding Decisions 0015/0024 scheduling, update the Study
  spec/test matrix and roadmap, append Northstar App Behavior through local MCP,
  and move this increment to `completed/`.
- Commit logical conventional-commit chunks. Do not push unless explicitly asked.
