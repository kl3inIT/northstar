package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Vocabulary content plus both provider-neutral FSRS scheduling summaries. */
public record VocabCardSummary(
        @NotNull UUID id,
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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lapseCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean leech,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean suspended,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean productionEnabled,
        Double productionRecallProbability,
        Double productionStabilityDays,
        Instant productionDueAt,
        VocabSchedulingState productionSchedulingState,
        Integer productionLapseCount,
        Boolean productionLeech,
        Integer productionReviewCount) {
}
