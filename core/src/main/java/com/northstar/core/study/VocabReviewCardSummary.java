package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** One direction-specific review prompt backed by a shared vocabulary item. */
public record VocabReviewCardSummary(
        @NotNull UUID id,
        @NotNull VocabReviewDirection direction,
        @NotNull String front,
        @NotNull String back,
        String metadata,
        @NotNull VocabLanguage language,
        String deck,
        UUID disciplineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double recallProbability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double halflifeHours,
        @NotNull Instant lastReviewedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean suspended,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
}
