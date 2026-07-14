# Runtime Caching Test Matrix

Reusable testing mechanics live in
[../guidelines/testing-harness.md](../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Typed exact cache access and invalidation | Automated | `ExactCacheTests` pins miss, put, hit, evict, unknown-name failure, and duplicate-spec failure. |
| Caffeine policy | Automated | `ExactCacheTests` inspects the native policy for per-cache TTL, maximum size, and recorded misses. |
| Web correctness keys | Automated | `WebResearchServiceTests` pins runtime provider switching and verifies disabling fallback cannot reuse a cached fallback result. |
| AI model catalog invalidation | Automated | `OpenAiModelCatalogTests` preloads a cached catalog, evicts by gateway id, and verifies the next load uses the current gateway definition. |
| HuggingNews detail reuse | Automated | `HuggingNewsFeedProviderTests` verifies a cached topic/slug skips upstream HTTP; existing feed code retains stale-on-error independently. |
| Cross-module cache wiring | Automated | `WebResearchControllerIntegrationTests` boots API/PostgreSQL and verifies the single manager contains all four core/integration cache names. |
| Semantic eligibility | Automated | `SemanticCachePolicyTests` pins the safe case and separately rejects disabled, writes, incomplete context, tools, memory, attachments, live data, and evidence-sensitive grading. |
| Disabled semantic provider | Automated | `SemanticCachePolicyTests` verifies valid writes remain no-op and every lookup misses. |
| Modulith boundary | Automated | `:core:test` verifies the open cache module can be consumed without adapters leaking into core. |
| Alternate CacheManager provider | Gap | Add an isolated provider contract suite when Redis or another distributed provider is introduced. |
| Semantic false-hit telemetry | Gap | Requires a real eligible workload and semantic provider; the general Assistant and graders remain deliberately disconnected. |
