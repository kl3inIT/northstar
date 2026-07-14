package com.northstar.core.search;

import java.util.UUID;

/** One stored file cited by attachment-scoped Assistant retrieval. */
public record AttachmentSource(
        UUID attachmentId,
        String filename,
        String mimeType,
        String url) {
}
