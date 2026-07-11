package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * One ISO week of study effort, plus the previous week for the "am I slipping"
 * comparison — a descriptive reference, not a quota. {@code bySkill} is
 * largest-first; sessions without a duration count toward {@code sessionCount}
 * but contribute zero minutes.
 */
public record StudySummary(
        @NotNull LocalDate weekStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sessionCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int previousWeekMinutes,
        @NotNull List<SkillEffort> bySkill) {

    public record SkillEffort(
            @NotNull String skill,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int minutes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sessions) {
    }
}
