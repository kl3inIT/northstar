package com.northstar.core.habit;

import com.northstar.core.shared.ColorName;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record HabitSummary(
        @NotNull UUID id,
        @NotNull String title,
        String cue,
        String notes,
        @NotNull ColorName color,
        @NotNull HabitStatus status,
        @NotNull String timezone,
        @NotNull HabitScheduleSummary schedule,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean paused,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
}
