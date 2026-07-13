package com.northstar.core.habit;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record HabitInsights(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @NotNull List<HabitInsightSummary> habits) {
}

