package com.northstar.core.artifact;

import java.util.Arrays;
import java.util.Locale;

/** Immutable content submitted to a temporary artifact provider. */
public record TemporaryArtifactWrite(String filename, String mediaType, byte[] data) {

    private static final int MAX_FILENAME_LENGTH = 255;

    public TemporaryArtifactWrite {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        filename = filename.strip();
        if (filename.length() > MAX_FILENAME_LENGTH
                || filename.equals(".")
                || filename.equals("..")
                || filename.indexOf('/') >= 0
                || filename.indexOf('\\') >= 0
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("filename is unsafe");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType is required");
        }
        mediaType = mediaType.strip().toLowerCase(Locale.ROOT);
        if (!mediaType.matches("[a-z0-9!#$%&'*+.^_`|~-]+/[a-z0-9!#$%&'*+.^_`|~-]+")) {
            throw new IllegalArgumentException("mediaType is invalid");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("artifact data is required");
        }
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
