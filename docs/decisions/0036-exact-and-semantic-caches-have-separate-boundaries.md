# 0036 — Exact and semantic caches have separate boundaries

Status: accepted.

## Context

Northstar had several independent in-process caches: web research imported
Caffeine directly, AI model discovery used an unbounded map with manual expiry,
and HuggingNews maintained its own detail map. This duplicated TTL/invalidation
mechanics and made a future multi-instance provider a call-site rewrite.

Semantic response caching was also being considered because Northstar already
uses pgvector. The existing vector table is durable knowledge searched by the
Assistant, however, and the general Assistant depends on tools, memory,
attachments, current data, and potentially mutating operations. Reusing a
similar old answer there can be incorrect or repeat/skip behavior.

## Decision

- Exact application caches use Spring Cache `CacheManager` through stable names,
  provider-neutral specs, and a typed wrapper.
- Caffeine is the conditional zero-infrastructure default. Every region is
  bounded, expires after write, rejects nulls, and records statistics.
- Web correctness context remains part of the key; gateway catalogs retain
  explicit invalidation; failures and secrets are never cached.
- HuggingNews feed stale-on-error remains an adapter-owned last-successful
  snapshot, while ordinary story details use the shared exact cache.
- Semantic response caching uses a separate port and fail-closed policy. Its
  default provider is disabled, and the current Assistant/graders are
  ineligible.
- Semantic responses never enter the durable knowledge `vector_store`. A real
  provider needs isolated storage and an explicitly safe stateless workload.

## Consequences

The currently useful caches run through one replaceable abstraction without
adding Redis. Multi-instance deployment can supply another `CacheManager` and
consume the same named specs. Semantic caching cannot be enabled accidentally
for stateful AI paths, and pgvector search remains free of cached-answer rows.

Cache data remains disposable. Chat memory, jobs, speech assets, learner audio,
attachments, tool traces, and client/model registries stay outside the cache
manager.
