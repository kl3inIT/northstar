package com.northstar.core.note;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A full note for the reading view: body plus its folder, tags, outgoing links
 * and backlinks. {@code version} is echoed back on update for optimistic
 * locking. The {@code @NotNull} marks exist for the OpenAPI contract — they
 * make the generated client fields required instead of all-optional.
 */
public record NoteDetail(
        @NotNull UUID id,
        @NotNull String title,
        @NotNull String slug,
        @NotNull String folderPath,
        @NotNull String contentMarkdown,
        @NotNull List<String> tags,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        long version,
        @NotNull List<NoteRef> outgoingLinks,
        @NotNull List<NoteRef> backlinks) {
}
