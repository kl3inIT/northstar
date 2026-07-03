package com.northstar.api.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body for POST /api/notes. {@code contentMarkdown} may be empty and {@code tags}
 * null; {@code folderPath} '' means the note lives at the root. Sizes mirror the
 * Flyway column widths (V1/V3).
 */
record CreateNoteRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 1024) String folderPath,
        String contentMarkdown,
        List<@NotBlank @Size(max = 64) String> tags) {
}
