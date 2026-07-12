# IELTS Speaking Estimate V1 — Plan

## Block A — Decision and domain contract

1. Record the superseding decision: provider measurements remain separate,
   while a rubric-based composite may produce an unofficial range.
2. Add the speaking rubric resource and structured criterion/estimate records.
3. Extend `SpeakingContentFeedback` and pure structural/aggregation tests.
4. Gate: `./gradlew --no-daemon compileJava compileTestJava :core:test`.

## Block B — Persistence and orchestration

1. Take the next free Flyway number and add `ielts_estimate` plus
   `estimate_version` to `speaking_feedback`, with legacy backfill.
2. Extend entity/summary contracts and `SpeakingCoach` prompt, evidence,
   evaluator claims, corrective retry, deterministic aggregation, and JSON.
3. Persist the scorer version with every new attempt.
4. Gate: backend compile/tests plus `pnpm -C web typecheck`.

## Block C — API and web

1. Regenerate `contracts/openapi.json` and the Hey API client.
2. Parse estimate JSON leniently at the web trust boundary.
3. Render criterion ranges, evidence, overall range, LOW confidence, and the
   explicit unofficial label separately from Azure/content 0-100 scores.
4. Cover current, legacy, malformed, loading, and history detail states.
5. Gate: backend compile/tests, web typecheck, and web build.

## Block D — Runtime, consolidation, and delivery

1. Run the terminating full backend context-load gate.
2. Walk the Speaking UI in a real browser and perform one live assessment
   using the existing local Azure and study-grader configuration.
3. Update Study spec/test matrix, roadmap, accepted decision, and the
   `Northstar App Behavior` note. Move this increment to `completed/`.
4. Commit logical conventional-commit chunks without staging other agents'
   work, push `main`, then monitor CI, image build, and deploy. Fix and repeat
   until green.

