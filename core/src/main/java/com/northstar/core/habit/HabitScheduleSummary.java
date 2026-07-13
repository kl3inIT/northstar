package com.northstar.core.habit;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public record HabitScheduleSummary(
        @NotNull HabitFrequencyType frequencyType,
        @NotNull Set<DayOfWeek> days,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int weeklyTarget,
        @NotNull LocalDate effectiveFrom) {
}
