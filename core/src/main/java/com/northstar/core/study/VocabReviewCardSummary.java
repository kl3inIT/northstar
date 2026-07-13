package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One due direction-specific review prompt backed by a shared vocabulary item. */
public record VocabReviewCardSummary(
        @NotNull UUID id,
        @NotNull UUID schedulingCardId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long schedulingVersion,
        @NotNull VocabReviewDirection direction,
        @NotNull String front,
        @NotNull String back,
        String metadata,
        @NotNull VocabLanguage language,
        String deck,
        UUID disciplineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double recallProbability,
        Double stabilityDays,
        @NotNull Instant dueAt,
        Instant lastReviewedAt,
        @NotNull VocabSchedulingState schedulingState,
        Integer learningStep,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lapseCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean leech,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean suspended,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @NotNull Instant previewedAt,
        @NotNull List<VocabRatingPreview> ratingPreviews) {
}
