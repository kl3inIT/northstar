package com.northstar.core.artifact;

/** Provider-neutral operational snapshot; all values are process-local estimates. */
public record TemporaryArtifactStoreStats(
        long currentEntries,
        long currentBytes,
        long hits,
        long misses,
        long evictions) {
}
