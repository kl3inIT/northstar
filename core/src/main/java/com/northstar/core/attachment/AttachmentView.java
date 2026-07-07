package com.northstar.core.attachment;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** An attachment's metadata — everything but the bytes. */
public record AttachmentView(
        @NotNull UUID id,
        @NotNull String filename,
        @NotNull String mimeType,
        long sizeBytes,
        @NotNull String sha256,
        @NotNull Instant createdAt) {
}
