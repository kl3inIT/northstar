package com.northstar.core.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Default semantic provider: safe misses until an explicit store is configured. */
public final class DisabledSemanticResponseCache implements SemanticResponseCache {

    @Override
    public Optional<SemanticCacheValue> find(SemanticCacheLookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return Optional.empty();
    }

    @Override
    public void put(SemanticCacheLookup lookup, SemanticCacheValue value, Duration ttl) {
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(value, "value");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Semantic cache TTL must be positive");
        }
    }

    @Override
    public void evictNamespace(String scope, String namespace) {
        if (scope == null || scope.isBlank() || namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Semantic cache scope and namespace are required");
        }
    }
}
