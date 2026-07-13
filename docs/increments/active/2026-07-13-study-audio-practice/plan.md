# Study Audio Practice — Plan

Execute in order. End each block with the relevant local static gates and a
logical conventional commit.

## Block A — Domain and storage foundation

- Add V46 `vocab_audio_attempt` with mode consistency and card cascade.
- Add provider-neutral attempt entity/repository/service, deterministic
  dictation scorer, retention/pin/delete behavior, and focused core tests.
- Extend `SpeechAssetService` with transient synthesis plus persist-existing-
  bytes so enrichment preview does not write.
- Gate: `./gradlew --no-daemon compileJava compileTestJava :core:test`.

## Block B — API orchestration and audio enrichment

- Add `AUDIO` enrichment, transient word/example MP3 preview, and Apply bindings.
- Add Word/Shadowing assessment persistence, Dictation, history, audio serving,
  pin, and delete endpoints.
- Extend controller/job integration tests and regenerate OpenAPI.
- Gate: targeted API/core tests plus the Java gate from Block A.

## Block C — Focused Vocabulary UI

- Replace reviewer Listen with applied-audio override plus browser fallback.
- Add Audio to enrichment preview/apply UI with word/example players.
- Add immediate recording playback, Word/Shadowing/Dictation modes, history,
  pin/delete, and same-provider trend.
- Regenerate Hey API client and add focused state tests.
- Gate: `pnpm -C web test`, `pnpm -C web typecheck`, `pnpm -C web build` plus
  the Java gate.

## Block D — Runtime verification and consolidation

- Run `./gradlew --no-daemon build` and clean-schema Hibernate validation.
- Drive browser fallback, enrichment override, recording/history, shadowing,
  dictation, and production reveal; verify console/network.
- Run one live configured TTS/Azure flow without logging secrets or retaining
  temporary fixtures beyond the explicit product attempt.
- Consolidate spec/test matrix/decision/roadmap/App Behavior and move the
  increment to `docs/increments/completed/`.
- Merge latest `origin/main`, rerun affected gates, and leave a clean branch.
