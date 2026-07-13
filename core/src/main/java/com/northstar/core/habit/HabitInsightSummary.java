package com.northstar.core.habit;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HabitInsightSummary(
        @NotNull HabitSummary habit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int expected,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int completed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int excused,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int consistency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentStreak,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int bestStreak,
        @NotNull List<HabitDaySummary> days) {
}
