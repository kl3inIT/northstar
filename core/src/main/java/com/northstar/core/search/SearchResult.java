package com.northstar.core.search;

import jakarta.validation.constraints.NotNull;

/**
 * One knowledge-base hit from hybrid search: enough to cite ({@code title},
 * {@code slug}) and to preview ({@code snippet} — a highlighted keyword
 * fragment when the hit came from full-text, else the matching chunk's text).
 */
public record SearchResult(
        @NotNull String title,
        @NotNull String slug,
        @NotNull String snippet) {
}
