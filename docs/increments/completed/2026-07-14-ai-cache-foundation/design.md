# AI Cache Foundation — Design

## Goal

Replace Northstar's scattered process-local caches with one provider-neutral
exact-cache foundation, while defining a safe seam for future semantic response
caching. Caffeine remains the zero-infrastructure default. A later deployment
may replace it with Redis without changing domain or adapter call sites.

## Decisions

### D1 — Spring Cache is the exact-cache boundary

Call sites depend on Spring's `CacheManager` through a small typed wrapper.
They do not import Caffeine or Redis types. Northstar supplies a Caffeine-backed
`CacheManager` only when the application has not supplied another provider.

Each cache declares a stable name, positive TTL, and positive maximum entry
count through provider-neutral `ExactCacheSpec` beans. The default provider
consumes those specs; a future Redis provider must consume the same contract.

### D2 — Caches are named and use-case-specific

The initial exact caches are:

- web search results;
- fetched web pages;
- discovered OpenAI-compatible model catalogs;
- HuggingNews story details.

Each cache keeps the TTL and bound that belonged to the original use case.
Nulls and failures are never cached. Caffeine records statistics so Actuator can
bind cache hit/miss/eviction metrics when an application exposes them.

### D3 — Correctness context belongs in the key

Web cache keys continue to include the selected provider, route, fallback mode,
fallback order, and request. Changing a route cannot reuse a result produced by
another route. Model catalogs remain keyed by gateway id and are explicitly
evicted after gateway changes. HuggingNews details remain keyed by topic/slug.

No key may contain an API key, credential, raw audio, attachment bytes, or other
secret material.

### D4 — Stale-on-error is not ordinary caching

HuggingNews feed refresh retains its last successful snapshot so an upstream
failure can return marked-stale news. That resilience behavior remains local to
the adapter. Only its ordinary detail cache moves to the shared exact-cache
foundation. Replacing the stale snapshot with a normal TTL cache would be a
behavior regression.

### D5 — Semantic response caching is isolated and fail-closed

Semantic response caching is a separate port and eligibility policy, not a mode
of the exact cache. A request is eligible only when all correctness inputs are
explicitly represented and it is stateless, read-only, tool-free, attachment-
free, memory-free, and independent of live data.

The current general assistant is therefore ineligible: it uses conversation
memory, personal context, dynamic tools, and potentially mutating operations.
Writing/Speaking assessment and other evidence-sensitive grading are also
ineligible. The default semantic provider is disabled and always misses.

### D6 — PgVector knowledge rows are not semantic-cache rows

Northstar's existing `vector_store` is searched as durable knowledge. Semantic
cache entries must never be inserted there because cached responses would then
leak into knowledge retrieval. Spring AI 2.0's supported semantic-cache provider
is Redis-specific; this increment does not add Redis or invent an incompatible
PgVector implementation.

### D7 — Cache is disposable acceleration, never durable state

Cache loss, process restart, provider replacement, or eviction must not change
Northstar's durable behavior. Jobs, chat clients, conversation memory, speech
assets, learner recordings, and attachment storage remain outside this cache
manager. No cache participates in a transaction or becomes a source of truth.

### D8 — Provider replacement is configuration, not a domain rewrite

The default Caffeine manager is conditional on a missing `CacheManager`. A
future Redis module may provide that bean and apply the same named specs. The
semantic response port remains separate because exact-key cache providers and
semantic similarity stores have different correctness and invalidation rules.

## Non-goals

- No Redis deployment or new infrastructure.
- No semantic caching of the assistant, graders, tools, or streaming responses.
- No database migration or reuse of the knowledge vector table.
- No caching of failures, secrets, audio, attachments, or mutable job state.
- No distributed invalidation in the single-instance deployment.

## Gates and verification

1. Focused cache tests pin TTL/bounds/configuration, typed access, invalidation,
   route-aware keys, catalog invalidation, and HuggingNews detail reuse.
2. Existing HuggingNews stale-on-error behavior remains unchanged.
3. The semantic eligibility policy rejects tools, memory, attachments, writes,
   live-data dependence, and missing correctness context; disabled cache misses.
4. Spring Modulith boundaries remain green.
5. Java compile/tests and web typecheck are green.
6. Consolidate the architecture/spec/test matrix/roadmap, add a decision record,
   move the increment to completed, and persist the architecture checkpoint in
   Northstar. No App Behavior note is needed because this has no user-facing UX.
