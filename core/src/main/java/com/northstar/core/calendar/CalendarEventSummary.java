package com.northstar.core.calendar;

import com.northstar.core.shared.ColorName;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for calendar events. {@code notes}, {@code disciplineId} and
 * {@code rrule} are genuinely nullable; the {@code @NotNull} marks make the
 * rest required in the generated OpenAPI client. A recurring master expands
 * into one summary per occurrence: same {@code id}, occurrence start/end,
 * {@code rrule} carried so clients know it is one buổi of a series.
 */
public record CalendarEventSummary(
        @NotNull UUID id,
        @NotNull String title,
        String notes,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,
        @NotNull ColorName color,
        UUID disciplineId,
        String rrule) {
}
