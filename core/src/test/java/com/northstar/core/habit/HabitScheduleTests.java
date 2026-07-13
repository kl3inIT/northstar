package com.northstar.core.habit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HabitScheduleTests {

    @Test
    void selectedDaysRoundTripThroughCompactMask() {
        HabitSchedule schedule = new HabitSchedule(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 7, 13), HabitFrequencyType.ON_DAYS,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 0);

        assertThat(schedule.days()).containsExactlyInAnyOrder(
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        assertThat(schedule.includes(DayOfWeek.TUESDAY)).isFalse();
        assertThat(schedule.appliesOn(LocalDate.of(2026, 7, 13))).isTrue();

        schedule.closeBefore(LocalDate.of(2026, 7, 20));
        assertThat(schedule.appliesOn(LocalDate.of(2026, 7, 19))).isTrue();
        assertThat(schedule.appliesOn(LocalDate.of(2026, 7, 20))).isFalse();
    }

    @Test
    void weeklyTargetHasNoArtificialDueWeekdays() {
        HabitSchedule schedule = new HabitSchedule(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 7, 13), HabitFrequencyType.WEEKLY_TARGET,
                Set.of(), 3);

        assertThat(schedule.days()).isEmpty();
        assertThat(schedule.weeklyTarget()).isEqualTo(3);
    }
}

