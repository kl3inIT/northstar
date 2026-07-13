package com.northstar.api.habit;

import com.northstar.core.habit.HabitFrequencyType;
import com.northstar.core.shared.ColorName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

record HabitRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 255) String cue,
        String notes,
        @NotNull ColorName color,
        @NotNull HabitFrequencyType frequencyType,
        @NotNull Set<DayOfWeek> days,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "7")
        int weeklyTarget,
        LocalDate effectiveFrom) {
}

