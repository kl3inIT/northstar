package com.northstar.core.habit;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record HabitDaySummary(@NotNull LocalDate date, @NotNull HabitDayState state) {
}

