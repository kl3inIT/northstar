package com.northstar.core.search;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * One hit from hybrid search: enough to preview ({@code snippet} — a
 * highlighted keyword fragment when the hit came from full-text, else the
 * matching chunk's text), to cite ({@code title}, {@code url} — an in-app
 * link: {@code /notes/&lt;slug&gt;} or {@code /api/files/&lt;id&gt;}), and to
 * read on ({@code slug}, note hits only — the get_note key; null for files).
 */
public record SearchResult(
        @NotNull String source,
        @NotNull String title,
        @Nullable String slug,
        @NotNull String url,
        @NotNull String snippet) {

    public static final String SOURCE_NOTE = "note";
    public static final String SOURCE_FILE = "file";
}
