package com.northstar.core.habit;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HabitTodaySummary(
        @NotNull HabitSummary habit,
        @NotNull HabitDayState todayState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dueToday,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int completedThisWeek,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int targetThisWeek,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int consistency30,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int consistency90,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentStreak,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int bestStreak,
        @NotNull List<HabitDaySummary> recentDays) {
}
