# AI Cache Foundation — Plan

Completed on 2026-07-14. Exact caches are migrated, the semantic boundary is
fail-closed and disabled, focused/full Java gates, Modulith verification, API
PostgreSQL context wiring, and web typecheck are green.

Execute in order. End each block with its focused tests before moving on.

## Block A — Exact-cache foundation

- Add the provider-neutral cache names/spec contract and typed Spring Cache
  wrapper in core.
- Add the conditional Caffeine default provider with per-cache TTL, bounds,
  null rejection, and statistics.
- Add focused configuration/wrapper tests.
- Gate: `./gradlew --no-daemon compileJava compileTestJava :core:test`.

## Block B — Migrate existing exact caches

- Move web search/page caches from direct Caffeine imports to the shared cache.
- Move OpenAI-compatible model catalog caching and invalidation.
- Move HuggingNews story-detail caching while retaining feed stale-on-error.
- Extend focused adapter/core tests for reuse, isolation, and invalidation.
- Gate: targeted integration tests plus the Block A Java gate.

## Block C — Semantic-cache safety boundary

- Add the provider-neutral semantic response cache port, request/value shapes,
  disabled provider, and fail-closed eligibility policy.
- Pin every unsafe assistant/grading condition in focused tests.
- Do not attach the cache to the current assistant or knowledge vector store.
- Gate: focused core tests plus the Block A Java gate.

## Block D — Full verification and consolidation

- Run the repository Java gate and `pnpm -C web typecheck`.
- Run broader affected tests/build checks in proportion to risk.
- Consolidate architecture/spec/test matrix/decision/roadmap, move the increment
  to completed, and capture the verified architecture checkpoint in Northstar.
