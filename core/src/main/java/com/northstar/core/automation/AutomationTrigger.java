package com.northstar.core.automation;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

public record AutomationTrigger(
        @NotNull AutomationTriggerKind kind,
        @NotNull LocalTime localTime,
        @NotNull Set<DayOfWeek> daysOfWeek,
        @NotNull String timezone,
        @NotNull int catchUpWindowMinutes) {

    public AutomationTrigger {
        if (kind == null) throw new IllegalArgumentException("trigger kind is required");
        if (localTime == null) throw new IllegalArgumentException("localTime is required");
        daysOfWeek = daysOfWeek == null || daysOfWeek.isEmpty()
                ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.copyOf(daysOfWeek);
        if (kind == AutomationTriggerKind.WEEKLY && daysOfWeek.size() != 1) {
            throw new IllegalArgumentException("A weekly trigger requires exactly one day of week");
        }
        timezone = timezone == null ? "" : timezone.strip();
        if (timezone.isBlank()) throw new IllegalArgumentException("timezone is required");
        try {
            ZoneId.of(timezone);
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone, exception);
        }
        if (catchUpWindowMinutes < 0 || catchUpWindowMinutes > 1440) {
            throw new IllegalArgumentException("catchUpWindowMinutes must be between 0 and 1440");
        }
        daysOfWeek = Set.copyOf(daysOfWeek);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
