# Runtime Caching Spec

## Exact application cache

Northstar uses Spring Cache's `CacheManager` as the provider boundary for
disposable exact-key acceleration. The default manager is Caffeine and is
created only when an application does not provide another manager. Cache call
sites use `ExactCache<K,V>` and stable names; only the default provider imports
Caffeine.

Every cache publishes an `ExactCacheSpec` with a positive TTL and maximum entry
count. The current regions are web search, web page, AI model catalog, and
HuggingNews story detail. Values are never authoritative: losing or evicting a
cache only causes the source/provider to be read again.

Web keys include provider, gateway/target route, fallback mode/order, and the
normalized request. Model catalogs are keyed and invalidated by gateway id.
HuggingNews details use topic/slug. Nulls and failures are never stored, and
keys contain no credentials or raw user content such as audio/attachments.

HuggingNews's last successful feed is intentionally not an ordinary cache. It
is retained by the adapter so an upstream failure can return a marked-stale
snapshot after its freshness TTL.

## Semantic response cache

Semantic response caching has a separate `SemanticResponseCache` port because
similarity lookup has different correctness and invalidation rules. The shipped
provider is disabled and always misses.

`SemanticCachePolicy` is fail-closed. A future caller must explicitly assert
that caching is enabled and the request is read-only, context-complete,
tool-free, memory-free, attachment-free, independent of live data, and not
evidence-sensitive. The lookup shape also requires owner scope, namespace,
model route, instruction/context fingerprints, prompt, and similarity floor.

Consequently, the current Assistant, tool calls, capture writes, current-data
answers, attachments, Writing/Speaking assessment, and personal-memory answers
are not eligible. Semantic entries must never share the durable knowledge
`vector_store`. A future provider requires an isolated store and a concrete
eligible workload before it is enabled.

## Operations

Caffeine caches reject null values and record statistics. Spring Boot Actuator
may bind cache metrics in deployables that expose them. Cache contents are
process-local in the current single-instance topology; there is no distributed
invalidation guarantee. A future Redis provider must consume the same named
specs and preserve the key/invalidation contracts.
