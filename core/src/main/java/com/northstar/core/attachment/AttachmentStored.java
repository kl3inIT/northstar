package com.northstar.core.attachment;

import java.util.UUID;

/**
 * A NEW attachment row was stored (dedupe hits on an existing sha256 do not
 * fire). Consumers read the bytes back through {@link AttachmentService}; the
 * search module embeds extractable content so uploads become searchable.
 */
public record AttachmentStored(UUID id) {
}
