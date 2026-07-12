# Vocabulary Production And Rich Enrichment — Plan

Status: completed 2026-07-12. All four blocks and their local/live gates passed;
the increment was consolidated into Decision 0026 and the Study sources of truth.

## Block A — Memory directions

- Add the Flyway/JPA fields for optional production memory and review-log direction.
- Add deck defaults and per-item production override.
- Expose a direction-aware review queue, answer check, and rating write.
- Pin migration-compatible behavior with core and API tests.
- Gate: `./gradlew --no-daemon compileJava compileTestJava :core:test`.

## Block B — Enrichment contracts and image adapter

- Add structured optional word formation.
- Add a provider-neutral image generation port and OpenAI/Nine Router adapter.
- Validate base64, media signatures, size bounds, provider errors, and model routing.
- Add the expiring background job with preview/apply/discard.
- Gate: Java compile/core tests plus adapter/API tests.

## Block C — Focused web workflow

- Add deck default and per-item production controls.
- Render direction-aware prompts, placeholders, listening rules, and sibling-safe queue state.
- Run enrichment in the background, notify on completion, show preview without scrolling,
  apply explicitly, and render an applied image only on the front.
- Render word-formation parts and family on the answer side when present.
- Gate: `pnpm -C web test`, `pnpm -C web typecheck`, and `pnpm -C web build`.

## Block D — Contract, live verification, and consolidation

- Regenerate OpenAPI and the typed client.
- Run full Gradle and web gates.
- Exercise recognition, production, background enrichment, preview/discard/apply,
  and front-image rendering in a real browser.
- Update Study spec/test matrix, append decision 0026, roadmap, increment status,
  and Northstar App Behavior; move this increment to completed.
- Commit logical conventional-commit chunks. Do not push.
