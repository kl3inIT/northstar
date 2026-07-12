package com.northstar.core.speech;

import java.util.Objects;

/** Provider response normalized before it enters Northstar's attachment vault. */
public record SpeechAudio(byte[] data, String mimeType, String format) {

    public SpeechAudio {
        Objects.requireNonNull(data, "data");
        data = data.clone();
        mimeType = required(mimeType, "mimeType");
        format = required(format, "format").toLowerCase();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
