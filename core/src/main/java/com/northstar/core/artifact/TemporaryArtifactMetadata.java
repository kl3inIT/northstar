package com.northstar.core.artifact;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable metadata for one immutable temporary artifact. */
public record TemporaryArtifactMetadata(
        UUID id,
        String filename,
        String mediaType,
        int size,
        String sha256,
        Instant createdAt,
        Instant expiresAt) {

    public TemporaryArtifactMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }
}
