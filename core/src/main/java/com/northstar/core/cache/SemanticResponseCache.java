package com.northstar.core.cache;

import java.time.Duration;
import java.util.Optional;

public interface SemanticResponseCache {

    Optional<SemanticCacheValue> find(SemanticCacheLookup lookup);

    void put(SemanticCacheLookup lookup, SemanticCacheValue value, Duration ttl);

    void evictNamespace(String scope, String namespace);
}
