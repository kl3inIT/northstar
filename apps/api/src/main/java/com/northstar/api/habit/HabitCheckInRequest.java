package com.northstar.api.habit;

import com.northstar.core.habit.HabitCheckInStatus;
import jakarta.validation.constraints.NotNull;

record HabitCheckInRequest(@NotNull HabitCheckInStatus status) {
}

