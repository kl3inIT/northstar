package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for one card: content plus the memory numbers the page and the
 * chat quiz reason about. {@code recallProbability} is computed at read time
 * for "now" — there is no due date to expose. {@code metadata} is the raw
 * JSON string ({"reading": ..., "example": ...}); clients parse what they
 * know and ignore the rest.
 */
public record VocabCardSummary(
        @NotNull UUID id,
        @NotNull String front,
        @NotNull String back,
        String metadata,
        UUID disciplineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double recallProbability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double halflifeHours,
        @NotNull Instant lastReviewedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean suspended,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
}
