package com.northstar.core.cache;

/**
 * Positive safety assertions required before semantic response caching.
 * Java's default {@code false} value deliberately makes this fail closed.
 */
public record SemanticCacheContext(
        boolean enabled,
        boolean readOnly,
        boolean contextComplete,
        boolean toolFree,
        boolean memoryFree,
        boolean attachmentFree,
        boolean liveDataIndependent,
        boolean evidenceInsensitive) {

    public static SemanticCacheContext disabled() {
        return new SemanticCacheContext(false, false, false, false,
                false, false, false, false);
    }
}
