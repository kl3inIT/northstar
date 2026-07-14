package com.northstar.core.search;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Safe API projection of an attachment's derived-index preparation state. */
public record AttachmentIndexView(
        UUID attachmentId,
        AttachmentIndexStatus status,
        @Nullable String errorCode,
        @Nullable Instant updatedAt) {
}
