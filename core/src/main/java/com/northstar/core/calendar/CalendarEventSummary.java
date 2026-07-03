package com.northstar.core.calendar;

import com.northstar.core.shared.ColorName;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for calendar events. {@code notes} and {@code disciplineId} are
 * genuinely nullable; the {@code @NotNull} marks make the rest required in the
 * generated OpenAPI client.
 */
public record CalendarEventSummary(
        @NotNull UUID id,
        @NotNull String title,
        String notes,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,
        @NotNull ColorName color,
        UUID disciplineId) {
}
