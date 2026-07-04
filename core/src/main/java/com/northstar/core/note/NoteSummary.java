package com.northstar.core.note;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A note as shown in a list or search result: no body, just a snippet + its
 * folder and tags. {@code @NotNull} marks make the generated OpenAPI client
 * fields required instead of all-optional.
 */
public record NoteSummary(
        @NotNull UUID id,
        @NotNull String title,
        @NotNull String slug,
        @NotNull String folderPath,
        @NotNull String snippet,
        @NotNull List<String> tags,
        @NotNull NoteStatus status,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {
}
