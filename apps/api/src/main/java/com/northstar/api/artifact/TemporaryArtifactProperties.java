package com.northstar.api.artifact;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "northstar.artifacts")
record TemporaryArtifactProperties(
        Duration ttl,
        Integer maximumArtifactBytes,
        Long maximumTotalBytes,
        Integer maximumEntries) {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final int DEFAULT_MAXIMUM_ARTIFACT_BYTES = 16 * 1024 * 1024;
    private static final long DEFAULT_MAXIMUM_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final int DEFAULT_MAXIMUM_ENTRIES = 100;

    TemporaryArtifactProperties {
        ttl = ttl == null ? DEFAULT_TTL : ttl;
        maximumArtifactBytes = maximumArtifactBytes == null
                ? DEFAULT_MAXIMUM_ARTIFACT_BYTES : maximumArtifactBytes;
        maximumTotalBytes = maximumTotalBytes == null
                ? DEFAULT_MAXIMUM_TOTAL_BYTES : maximumTotalBytes;
        maximumEntries = maximumEntries == null ? DEFAULT_MAXIMUM_ENTRIES : maximumEntries;
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("northstar.artifacts.ttl must be positive");
        }
        if (maximumArtifactBytes <= 0) {
            throw new IllegalArgumentException(
                    "northstar.artifacts.maximum-artifact-bytes must be positive");
        }
        if (maximumTotalBytes < maximumArtifactBytes) {
            throw new IllegalArgumentException(
                    "northstar.artifacts.maximum-total-bytes must fit one maximum artifact");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException(
                    "northstar.artifacts.maximum-entries must be positive");
        }
        if (minimumEntryWeight(maximumTotalBytes, maximumEntries) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "northstar.artifacts entry weight exceeds Caffeine's integer weight limit");
        }
    }

    int minimumEntryWeight() {
        return Math.toIntExact(minimumEntryWeight(maximumTotalBytes, maximumEntries));
    }

    private static long minimumEntryWeight(long totalBytes, int entries) {
        return Math.max(1, Math.floorDiv(totalBytes, entries));
    }
}
