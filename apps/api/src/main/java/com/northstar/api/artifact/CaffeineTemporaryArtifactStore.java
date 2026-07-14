package com.northstar.api.artifact;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.northstar.core.artifact.TemporaryArtifact;
import com.northstar.core.artifact.TemporaryArtifactMetadata;
import com.northstar.core.artifact.TemporaryArtifactScope;
import com.northstar.core.artifact.TemporaryArtifactStore;
import com.northstar.core.artifact.TemporaryArtifactStoreStats;
import com.northstar.core.artifact.TemporaryArtifactWrite;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class CaffeineTemporaryArtifactStore implements TemporaryArtifactStore {

    private final TemporaryArtifactProperties properties;
    private final Clock clock;
    private final Cache<ArtifactKey, TemporaryArtifact> artifacts;

    CaffeineTemporaryArtifactStore(TemporaryArtifactProperties properties, Clock clock, Ticker ticker) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(ticker, "ticker");
        int minimumEntryWeight = properties.minimumEntryWeight();
        this.artifacts = Caffeine.newBuilder()
                .maximumWeight(properties.maximumTotalBytes())
                .weigher((ArtifactKey key, TemporaryArtifact value) ->
                        Math.max(value.metadata().size(), minimumEntryWeight))
                .expireAfterWrite(properties.ttl())
                .ticker(ticker)
                .recordStats()
                .build();
    }

    @Override
    public java.time.Duration retention() {
        return properties.ttl();
    }

    @Override
    public TemporaryArtifactMetadata put(TemporaryArtifactScope scope, TemporaryArtifactWrite write) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(write, "write");
        byte[] data = write.data();
        if (data.length > properties.maximumArtifactBytes()) {
            throw new IllegalArgumentException("Temporary artifact exceeds "
                    + properties.maximumArtifactBytes() + " bytes");
        }
        Instant createdAt = clock.instant();
        TemporaryArtifactMetadata metadata = new TemporaryArtifactMetadata(UUID.randomUUID(),
                write.filename(), write.mediaType(), data.length, sha256(data), createdAt,
                createdAt.plus(properties.ttl()));
        artifacts.put(new ArtifactKey(scope, metadata.id()), new TemporaryArtifact(metadata, data));
        return metadata;
    }

    @Override
    public Optional<TemporaryArtifactMetadata> find(TemporaryArtifactScope scope, UUID artifactId) {
        return findArtifact(scope, artifactId).map(TemporaryArtifact::metadata);
    }

    @Override
    public Optional<TemporaryArtifact> peek(TemporaryArtifactScope scope, UUID artifactId) {
        return findArtifact(scope, artifactId);
    }

    @Override
    public Optional<TemporaryArtifact> consume(TemporaryArtifactScope scope, UUID artifactId) {
        return Optional.ofNullable(artifacts.asMap().remove(key(scope, artifactId)));
    }

    @Override
    public boolean delete(TemporaryArtifactScope scope, UUID artifactId) {
        return artifacts.asMap().remove(key(scope, artifactId)) != null;
    }

    @Override
    public long deleteSession(String ownerScope, String sessionId) {
        String owner = required(ownerScope, "ownerScope");
        String session = required(sessionId, "sessionId");
        List<ArtifactKey> keys = artifacts.asMap().keySet().stream()
                .filter(key -> key.scope.ownerScope().equals(owner)
                        && key.scope.sessionId().equals(session))
                .toList();
        artifacts.invalidateAll(keys);
        artifacts.cleanUp();
        return keys.size();
    }

    @Override
    public TemporaryArtifactStoreStats stats() {
        artifacts.cleanUp();
        long bytes = artifacts.asMap().values().stream()
                .mapToLong(value -> value.metadata().size())
                .sum();
        var stats = artifacts.stats();
        return new TemporaryArtifactStoreStats(artifacts.estimatedSize(), bytes,
                stats.hitCount(), stats.missCount(), stats.evictionCount());
    }

    private Optional<TemporaryArtifact> findArtifact(TemporaryArtifactScope scope, UUID artifactId) {
        return Optional.ofNullable(artifacts.getIfPresent(key(scope, artifactId)));
    }

    private static ArtifactKey key(TemporaryArtifactScope scope, UUID artifactId) {
        return new ArtifactKey(Objects.requireNonNull(scope, "scope"),
                Objects.requireNonNull(artifactId, "artifactId"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ArtifactKey(TemporaryArtifactScope scope, UUID artifactId) {
    }
}
