package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Server-computed outcome shown beneath one learner rating button. */
public record VocabRatingPreview(
        @NotNull VocabReviewLog.Rating rating,
        @NotNull VocabSchedulingState nextState,
        @NotNull Instant dueAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long intervalSeconds,
        @NotNull String intervalLabel) {
}

