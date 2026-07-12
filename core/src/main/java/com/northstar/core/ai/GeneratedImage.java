package com.northstar.core.ai;

import java.util.Arrays;

/** Provider-neutral raster output; callers own persistence and presentation. */
public record GeneratedImage(byte[] data, String mediaType) {

    public GeneratedImage {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("image data is required");
        }
        data = Arrays.copyOf(data, data.length);
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("image mediaType is required");
        }
        mediaType = mediaType.strip().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}

