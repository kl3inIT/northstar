package com.northstar.api.calendar;

import com.northstar.core.shared.ColorName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Create/update payload for a calendar event. Title/rrule sizes mirror the V6
 * column widths; rrule (RFC 5545 subset) marks a recurring series and null
 * means one-off.
 */
record CalendarEventRequest(
        @NotBlank @Size(max = 512) String title,
        @Size(max = 10_000) String notes,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,
        @NotNull ColorName color,
        UUID disciplineId,
        @Size(max = 512) String rrule) {
}
