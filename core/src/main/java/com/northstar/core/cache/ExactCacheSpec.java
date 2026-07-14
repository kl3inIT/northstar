package com.northstar.core.cache;

import java.time.Duration;

public record ExactCacheSpec(String name, Duration ttl, long maximumSize) {

    public ExactCacheSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cache name is required");
        }
        name = name.strip();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive: " + name);
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Cache maximum size must be positive: " + name);
        }
    }
}
