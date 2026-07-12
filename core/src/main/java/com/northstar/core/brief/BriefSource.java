package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BriefSource(
        @NotBlank String label,
        @NotBlank String author,
        @NotBlank String highlight,
        @NotBlank String text,
        @NotBlank String url,
        @NotNull Instant publishedAt) {
}
