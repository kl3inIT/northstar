package com.northstar.core.artifact;

import java.util.Arrays;
import java.util.Objects;

/** Immutable temporary artifact content plus metadata. */
public record TemporaryArtifact(TemporaryArtifactMetadata metadata, byte[] data) {

    public TemporaryArtifact {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(data, "data");
        if (data.length != metadata.size()) {
            throw new IllegalArgumentException("artifact data size does not match metadata");
        }
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
