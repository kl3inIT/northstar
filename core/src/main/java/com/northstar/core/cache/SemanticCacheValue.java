package com.northstar.core.cache;

import java.time.Instant;
import java.util.Map;

public record SemanticCacheValue(String response, Instant createdAt, Map<String, String> metadata) {

    public SemanticCacheValue {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Cached response is required");
        }
        response = response.strip();
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
