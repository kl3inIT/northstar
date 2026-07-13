package com.northstar.core.habit;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "habit_schedule")
class HabitSchedule extends BaseEntity {

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false, length = 24)
    private HabitFrequencyType frequencyType;

    @Column(name = "days_mask", nullable = false)
    private short daysMask;

    @Column(name = "weekly_target", nullable = false)
    private short weeklyTarget;

    protected HabitSchedule() {
    }

    HabitSchedule(UUID id, UUID habitId, LocalDate effectiveFrom, HabitFrequencyType type,
            Set<DayOfWeek> days, int weeklyTarget) {
        super(id);
        this.habitId = habitId;
        this.effectiveFrom = effectiveFrom;
        revise(type, days, weeklyTarget);
    }

    UUID habitId() { return habitId; }
    LocalDate effectiveFrom() { return effectiveFrom; }
    LocalDate effectiveUntil() { return effectiveUntil; }
    HabitFrequencyType frequencyType() { return frequencyType; }
    int weeklyTarget() { return weeklyTarget; }

    Set<DayOfWeek> days() {
        return java.util.Arrays.stream(DayOfWeek.values())
                .filter(this::includes).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    boolean appliesOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveUntil == null || !date.isAfter(effectiveUntil));
    }

    boolean includes(DayOfWeek day) {
        return (daysMask & (1 << (day.getValue() - 1))) != 0;
    }

    void closeBefore(LocalDate nextEffectiveFrom) {
        effectiveUntil = nextEffectiveFrom.minusDays(1);
    }

    void revise(HabitFrequencyType type, Set<DayOfWeek> days, int target) {
        frequencyType = type;
        daysMask = (short) (type == HabitFrequencyType.ON_DAYS ? mask(days) : 0);
        weeklyTarget = (short) (type == HabitFrequencyType.WEEKLY_TARGET ? target : days.size());
    }

    private static int mask(Set<DayOfWeek> days) {
        return days.stream().mapToInt(day -> 1 << (day.getValue() - 1)).reduce(0, (a, b) -> a | b);
    }
}
