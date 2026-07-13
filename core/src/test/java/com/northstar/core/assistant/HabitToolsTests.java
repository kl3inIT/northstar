package com.northstar.core.assistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.northstar.core.habit.HabitCheckInStatus;
import com.northstar.core.habit.HabitFrequencyType;
import com.northstar.core.habit.HabitService;
import com.northstar.core.shared.ColorName;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class HabitToolsTests {

    @Test
    void createDefaultsToDailyAndUsesStableColorEnum() {
        HabitService service = mock(HabitService.class);
        HabitTools tools = new HabitTools(service);

        tools.createHabit("Read", "After dinner", null, null, null,
                "Minimum ten minutes", "green");

        verify(service).create(eq("Read"), eq("After dinner"), eq("Minimum ten minutes"),
                eq(ColorName.GREEN), any(), eq(HabitFrequencyType.ON_DAYS),
                eq(java.util.Set.copyOf(List.of(DayOfWeek.values()))), eq(1), any());
    }

    @Test
    void checkInParsesExplicitLocalDateAndRejectsUnknownColor() {
        HabitService service = mock(HabitService.class);
        HabitTools tools = new HabitTools(service);
        String id = java.util.UUID.randomUUID().toString();

        tools.setHabitCheckIn(id, "2026-07-13", HabitCheckInStatus.DONE);
        verify(service).checkIn(eq(java.util.UUID.fromString(id)),
                eq(java.time.LocalDate.of(2026, 7, 13)), eq(HabitCheckInStatus.DONE), any());

        assertThatThrownBy(() -> tools.createHabit("Read", null, null, null, null, null, "teal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLUE, GREEN");
    }
}

