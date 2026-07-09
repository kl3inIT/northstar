package com.northstar.api.note;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body for PUT /api/notes/{id}. {@code version} is the version the client
 * loaded (optimistic locking): when present and stale the update fails with
 * 409 instead of overwriting a concurrent edit.
 */
record UpdateNoteRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 1024) String folderPath,
        String contentMarkdown,
        List<@NotBlank @Size(max = 64) String> tags,
        @Nullable @Schema(nullable = true) UUID projectId,
        Long version) {
}
